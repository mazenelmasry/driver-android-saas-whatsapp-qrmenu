package app.qrmenu.driver.network.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DTO layer is checked against the SAME contract the backend will enforce.
 *
 * `src/test/resources/contract/driver.v1.json` is a JSON rendering of this
 * repo's `openapi/driver.v1.yaml`. Today that YAML is the single source of truth
 * for both sides, because the backend has not been built yet. When it is, the
 * source of truth MOVES to the backend repo (enforced there against real
 * responses, exactly as `openapi/pos.v1.yaml` is) and this JSON becomes the
 * mirror of that file. Either way: change the YAML first, regenerate this JSON,
 * then fix whatever this test names.
 *
 * Regeneration (uses the backend repo's vendored symfony/yaml — read only):
 *
 *   php -r "require 'C:/laragon/www/filamentv4-saas-whatsapp-qrmenu/vendor/autoload.php';
 *     file_put_contents('core/network/src/test/resources/contract/driver.v1.json',
 *       json_encode(Symfony\Component\Yaml\Yaml::parseFile('openapi/driver.v1.yaml'),
 *         JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE));"
 *
 * What is enforced, per (DTO to schema) pair:
 *
 *  1. CRASH SAFETY — a field the parser REQUIRES (non-nullable, no default) must
 *     be `required` AND non-nullable in the schema. Otherwise a perfectly legal
 *     response kills the whole decode, not just that field.
 *  2. NULL TRAPS — a schema-nullable field must be nullable on the DTO.
 *  3. TYPE COMPATIBILITY — a schema `number` must never land on an integer type:
 *     a fractional fee or a cash amount would be truncated.
 *  4. PHANTOM FIELDS — a DTO field the contract does not declare reads as data
 *     while being fed by nothing.
 *
 * Consuming a SUBSET of the schema is always fine — that is how a client should
 * behave, and why `ignoreUnknownKeys` is on.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackendContractMirrorTest {

    private val spec: JsonObject by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream("/contract/driver.v1.json")) {
            "contract/driver.v1.json missing from test resources"
        }.bufferedReader().readText()
        Json.parseToJsonElement(text).jsonObject
    }

    private val schemas: JsonObject by lazy {
        spec.getValue("components").jsonObject.getValue("schemas").jsonObject
    }

    /**
     * DTO fields the contract does not declare. Empty on purpose — this surface
     * is new, so there is no legacy to freeze, and it must stay that way.
     */
    private val knownPhantoms: Map<String, Set<String>> = emptyMap()

    /**
     * Follows `$ref`, and merges `allOf` — which is how `DriverOrderAssigned`
     * extends the offered shape.
     */
    private fun resolve(schema: JsonObject): JsonObject {
        var current = schema
        while (true) {
            val ref = current["\$ref"]?.jsonPrimitive?.content
            if (ref != null) {
                val target = schemas.getValue(ref.substringAfterLast('/')).jsonObject
                val siblingNullable = current["nullable"]?.jsonPrimitive?.booleanOrNull == true
                current = if (siblingNullable && target["nullable"] == null) {
                    JsonObject(target + ("nullable" to JsonPrimitive(true)))
                } else {
                    target
                }
                continue
            }
            val allOf = current["allOf"]?.jsonArray ?: return current
            val properties = mutableMapOf<String, JsonElement>()
            val required = mutableListOf<JsonElement>()
            for (part in allOf) {
                val resolved = resolve(part.jsonObject)
                resolved["properties"]?.jsonObject?.let { properties += it }
                resolved["required"]?.jsonArray?.let { required.addAll(it) }
            }
            return JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(properties),
                    "required" to JsonArray(required),
                ),
            )
        }
    }

    private fun mirror(
        dtoName: String,
        descriptor: SerialDescriptor,
        schemaName: String,
        violations: MutableList<String>,
    ) = mirrorAgainst(dtoName, descriptor, schemas.getValue(schemaName).jsonObject, schemaName, violations)

    private fun mirrorAgainst(
        dtoName: String,
        descriptor: SerialDescriptor,
        rawSchema: JsonObject,
        schemaName: String,
        violations: MutableList<String>,
    ) {
        val schema = resolve(rawSchema)
        val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
        val phantoms = knownPhantoms[dtoName].orEmpty()

        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            val element = descriptor.getElementDescriptor(i)
            val where = "$dtoName.$name (schema $schemaName)"

            val property = properties[name]?.jsonObject
            if (property == null) {
                if (name !in phantoms) {
                    violations += "$where: phantom — the contract does not declare this field. " +
                        "Either the backend grew it (add it to driver.v1.yaml first) or it is dead weight."
                }
                continue
            }
            if (name in phantoms) {
                violations += "$where: listed in knownPhantoms but the contract DOES declare it — " +
                    "remove it from the allowlist."
            }

            val resolved = resolve(property)
            val schemaType = resolved["type"]?.jsonPrimitive?.content
            val schemaNullable = resolved["nullable"]?.jsonPrimitive?.booleanOrNull == true

            val parserRequires = !descriptor.isElementOptional(i) && !element.isNullable
            if (parserRequires && (name !in required || schemaNullable)) {
                violations += "$where: the parser REQUIRES this (non-nullable, no default) but the contract says " +
                    (if (schemaNullable) "it can be null" else "it can be absent") +
                    " — a legal response would throw and fail the whole decode."
            }

            if (schemaNullable && !element.isNullable) {
                violations += "$where: the contract says this can be null but the DTO is non-nullable."
            }

            if (schemaType != null && !kindsCompatible(schemaType, element)) {
                violations += "$where: contract type '$schemaType' vs DTO kind '${element.kind}' — incompatible."
            }
        }
    }

    private fun kindsCompatible(schemaType: String, element: SerialDescriptor): Boolean = when (schemaType) {
        "string" -> element.kind == PrimitiveKind.STRING || element.kind == SerialKind.ENUM
        "boolean" -> element.kind == PrimitiveKind.BOOLEAN
        "integer" -> element.kind in setOf(
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            PrimitiveKind.SHORT,
            PrimitiveKind.BYTE,
            // Widening an integral value into a Double is safe.
            PrimitiveKind.DOUBLE,
            PrimitiveKind.FLOAT,
        )
        // Never an Int: this surface's `number`s are money and distances.
        "number" -> element.kind == PrimitiveKind.DOUBLE || element.kind == PrimitiveKind.FLOAT
        "array" -> element.kind == StructureKind.LIST
        "object" -> element.kind == StructureKind.CLASS ||
            element.kind == StructureKind.OBJECT ||
            element.kind == StructureKind.MAP
        else -> true
    }

    @Test
    fun `every mirrored DTO matches the driver contract`() {
        val violations = mutableListOf<String>()

        // `Accepted` is a shared RESPONSE, not a named schema — its body is
        // declared inline, so it is mirrored from where it actually lives.
        mirrorAgainst(
            "AcceptedDto",
            AcceptedDto.serializer().descriptor,
            spec.getValue("components").jsonObject
                .getValue("responses").jsonObject
                .getValue("Accepted").jsonObject
                .getValue("content").jsonObject
                .getValue("application/json").jsonObject
                .getValue("schema").jsonObject,
            "Accepted (response)",
            violations,
        )
        mirror("AppVersionDto", AppVersionDto.serializer().descriptor, "AppVersion", violations)
        mirror("LocationPointDto", LocationPointDto.serializer().descriptor, "LocationPoint", violations)
        mirror("BranchDto", BranchDto.serializer().descriptor, "Branch", violations)
        mirror("OrderItemDto", OrderItemDto.serializer().descriptor, "OrderItem", violations)
        mirror("DriverDto", DriverDto.serializer().descriptor, "Driver", violations)
        mirror("RestaurantLinkDto", RestaurantLinkDto.serializer().descriptor, "RestaurantLink", violations)
        mirror("OfferDto", OfferDto.serializer().descriptor, "Offer", violations)
        // The ASSIGNED shape is the superset, so it is what the single
        // DriverOrderDto is checked against — see that class's KDoc for why one
        // Kotlin type covers both wire shapes.
        mirror("DriverOrderDto", DriverOrderDto.serializer().descriptor, "DriverOrderAssigned", violations)
        mirror(
            "AvailabilityContextDto",
            AvailabilityContextDto.serializer().descriptor,
            "AvailabilityContext",
            violations,
        )
        mirror("LedgerSummaryDto", LedgerSummaryDto.serializer().descriptor, "LedgerSummary", violations)
        mirror("LedgerEntryDto", LedgerEntryDto.serializer().descriptor, "LedgerEntry", violations)
        mirror("SettlementDto", SettlementDto.serializer().descriptor, "Settlement", violations)

        assertTrue(
            "The DTO layer drifted from contract/driver.v1.json (${violations.size} violation(s)):\n - " +
                violations.joinToString("\n - "),
            violations.isEmpty(),
        )
    }

    /**
     * Decision 23, guarded at the contract itself.
     *
     * Before acceptance a driver must not receive the customer's name, phone or
     * full address. The rule holds because the OFFERED schema does not DECLARE
     * those fields at all — they are structurally absent, not nulled — so it
     * cannot be broken by forgetting to blank something. If anyone ever adds
     * them to `DriverOrderOffered`, this fails before a single customer's number
     * is handed to a driver who has not taken the job.
     */
    @Test
    fun `the offered shape cannot carry the customer or the address`() {
        val offered = schemas.getValue("DriverOrderOffered").jsonObject
        val properties = offered.getValue("properties").jsonObject

        for (forbidden in listOf("customer", "delivery_address")) {
            assertFalse(
                "DriverOrderOffered declares '$forbidden' — decision 23 says a driver sees the area, the " +
                    "distance and the money BEFORE accepting, and the customer only after.",
                properties.containsKey(forbidden),
            )
        }

        // And the assigned shape must still carry them, or the driver who DID
        // accept has nowhere to deliver to.
        val assigned = resolve(schemas.getValue("DriverOrderAssigned").jsonObject)
        val assignedProperties = assigned.getValue("properties").jsonObject
        assertTrue(assignedProperties.containsKey("customer"))
        assertTrue(assignedProperties.containsKey("delivery_address"))
    }

    /**
     * The error vocabulary is frozen (CLAUDE.md, error codes): the app translates
     * by CODE, because the server speaks ar/en only while the driver may be
     * reading Urdu, Bengali or Hindi. A code added to the contract but not to the
     * enum here reaches a driver as a generic message; one added to the enum but
     * not to the contract is a translation nobody will ever see.
     */
    @Test
    fun `the error code enum is exactly the frozen contract list`() {
        val fromContract = schemas.getValue("ErrorCode").jsonObject
            .getValue("enum").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(
            "DriverErrorCode drifted from the contract's frozen ErrorCode list.",
            fromContract,
            app.qrmenu.driver.network.errors.DriverErrorCode.entries.map { it.wire },
        )
    }

    /** The issue reasons are a closed list too — an unlisted one has no translated label. */
    @Test
    fun `the issue reasons match the contract`() {
        val fromContract = spec.getValue("paths").jsonObject
            .getValue("/driver/orders/{id}/issue").jsonObject
            .getValue("post").jsonObject
            .getValue("requestBody").jsonObject
            .getValue("content").jsonObject
            .getValue("application/json").jsonObject
            .getValue("schema").jsonObject
            .getValue("properties").jsonObject
            .getValue("code").jsonObject
            .getValue("enum").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(fromContract, DriverIssueCode.entries.map { it.wire })
    }

    @Test
    fun `the contract file itself is sane`() {
        assertTrue(spec.getValue("paths").jsonObject.isNotEmpty())
        assertTrue(schemas.containsKey("DriverOrderOffered"))
        assertTrue(schemas.containsKey("DriverOrderAssigned"))
        assertTrue(schemas.containsKey("LedgerSummary"))
        assertTrue(schemas.containsKey("ErrorCode"))
    }
}
