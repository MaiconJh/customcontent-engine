package com.customcontentengine.adapter.yaml;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlDefinitionLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsValidBundledDefinitions() {
        DefinitionRegistry registry = loader().load(Path.of("src/main/resources/definitions.yml").toFile());

        assertTrue(registry.findBlock(new CustomBlockId("ruby_ore")).isPresent());
        assertTrue(registry.findBlockByNumericId((short) 1).isPresent());
        assertTrue(registry.findItem(new CustomItemId("ruby_pickaxe")).isPresent());
        assertEquals(
                List.of(new MechanicId("area_break")),
                registry.mechanicBindings().mechanicIdsFor(
                        new CustomItemId("ruby_pickaxe"),
                        MechanicTrigger.ON_BLOCK_BREAK));
    }

    @Test
    void loadsYamlWithoutMechanics() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYaml()).toFile());

        assertTrue(registry.mechanicBindings().bindings().isEmpty());
    }

    @Test
    void loadsYamlWithoutMining() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYaml()).toFile());

        assertTrue(registry.findBlock(new CustomBlockId("ruby_ore")).orElseThrow().miningHardness().isEmpty());
        assertTrue(registry.findItem(new CustomItemId("ruby_pickaxe")).orElseThrow().miningSpeed().isEmpty());
    }

    @Test
    void loadsBlockMiningHardness() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMining()).toFile());

        assertEquals(
                6.0D,
                registry.findBlock(new CustomBlockId("ruby_ore")).orElseThrow().miningHardness().orElseThrow().value());
    }

    @Test
    void loadsItemMiningSpeed() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMining()).toFile());

        assertEquals(
                8.0D,
                registry.findItem(new CustomItemId("ruby_pickaxe")).orElseThrow().miningSpeed().orElseThrow().value());
    }

    @Test
    void loadsOnBlockBreakMechanicBinding() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMechanics()).toFile());

        assertEquals(
                List.of(new MechanicId("area_break")),
                registry.mechanicBindings().mechanicIdsFor(
                        new CustomItemId("ruby_pickaxe"),
                        MechanicTrigger.ON_BLOCK_BREAK));
    }

    @Test
    void rejectsMissingSchema() throws IOException {
        assertInvalid("""
                blocks: {}
                items: {}
                """, "schema must be an integer");
    }

    @Test
    void rejectsUnsupportedSchema() throws IOException {
        assertInvalid(validYaml().replace("schema: 1", "schema: 2"), "schema must be 1 but was 2");
    }

    @Test
    void rejectsDuplicateNumericId() throws IOException {
        assertInvalid("""
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    drops:
                      - item: ruby
                        amount: 1
                  sapphire_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1002
                    required_tool: ruby_pickaxe
                    drops:
                      - item: sapphire
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                """, "Duplicate block numeric_id: 1");
    }

    @Test
    void rejectsUnknownRequiredTool() throws IOException {
        assertInvalid(validYaml().replace("required_tool: ruby_pickaxe", "required_tool: missing_pickaxe"),
                "required_tool references unknown item: missing_pickaxe");
    }

    @Test
    void rejectsDropAmountLessThanOne() throws IOException {
        assertInvalid(validYaml().replace("amount: 1", "amount: 0"), "drops[0].amount must be greater than zero but was 0");
    }

    @Test
    void rejectsNegativeBlockMiningHardness() throws IOException {
        assertInvalid(
                validYamlWithMining().replace("hardness: 6.0", "hardness: -1.0"),
                "blocks.ruby_ore.mining.hardness must be greater than zero but was -1.0");
    }

    @Test
    void rejectsZeroBlockMiningHardness() throws IOException {
        assertInvalid(
                validYamlWithMining().replace("hardness: 6.0", "hardness: 0.0"),
                "blocks.ruby_ore.mining.hardness must be greater than zero but was 0.0");
    }

    @Test
    void rejectsZeroItemMiningSpeed() throws IOException {
        assertInvalid(
                validYamlWithMining().replace("speed: 8.0", "speed: 0.0"),
                "items.ruby_pickaxe.mining.speed must be greater than zero but was 0.0");
    }

    @Test
    void rejectsNegativeItemMiningSpeed() throws IOException {
        assertInvalid(
                validYamlWithMining().replace("speed: 8.0", "speed: -1.0"),
                "items.ruby_pickaxe.mining.speed must be greater than zero but was -1.0");
    }

    @Test
    void rejectsBlockMiningThatIsNotASection() throws IOException {
        assertInvalid(
                validYamlWithMining().replace("""
                    mining:
                      hardness: 6.0
                """, """
                    mining: 6.0
                """),
                "blocks.ruby_ore.mining must be a YAML section");
    }

    @Test
    void rejectsItemMiningThatIsNotASection() throws IOException {
        assertInvalid(
                validYamlWithMining().replace("""
                    mining:
                      speed: 8.0
                """, """
                    mining: 8.0
                """),
                "items.ruby_pickaxe.mining must be a YAML section");
    }

    @Test
    void rejectsNonPositiveBlockCustomModelData() throws IOException {
        assertInvalid(validYaml().replace("custom_model_data: 1001", "custom_model_data: 0"),
                "blocks.ruby_ore.custom_model_data must be greater than zero but was 0");
    }

    @Test
    void rejectsNonPositiveItemCustomModelData() throws IOException {
        assertInvalid(validYaml().replace("custom_model_data: 2001", "custom_model_data: 0"),
                "items.ruby_pickaxe.custom_model_data must be greater than zero but was 0");
    }

    @Test
    void rejectsNonPositiveDurability() throws IOException {
        assertInvalid(validYaml().replace("durability: 500", "durability: 0"),
                "items.ruby_pickaxe.attributes.durability must be greater than zero but was 0");
    }

    @Test
    void rejectsInvalidBlockId() throws IOException {
        assertInvalid(validYaml().replace("ruby_ore:", "ruby ore:"), "blocks.ruby ore has invalid id");
    }

    @Test
    void rejectsInvalidItemId() throws IOException {
        assertInvalid(validYaml().replace("ruby_pickaxe:", "ruby pickaxe:"), "items.ruby pickaxe has invalid id");
    }

    @Test
    void rejectsInvalidMechanicTrigger() throws IOException {
        assertInvalid(
                validYamlWithMechanics().replace("on_block_break:", "on_place:"),
                "items.ruby_pickaxe.mechanics.on_place is an unknown mechanic trigger");
    }

    @Test
    void rejectsInvalidMechanicId() throws IOException {
        assertInvalid(
                validYamlWithMechanics().replace("area_break", "area-break"),
                "items.ruby_pickaxe.mechanics.on_block_break[0] has invalid mechanic id");
    }

    @Test
    void rejectsMechanicsThatAreNotASection() throws IOException {
        assertInvalid(
                validYamlWithMechanics().replace("""
                    mechanics:
                      on_block_break:
                        - area_break
                """, """
                    mechanics:
                      - area_break
                """),
                "items.ruby_pickaxe.mechanics must be a YAML section");
    }

@Test
    void rejectsMechanicTriggerValueThatIsNotAList() throws IOException {
        assertInvalid(
                validYamlWithMechanics().replace("""
                      on_block_break:
                        - area_break
                """, """
                      on_block_break: area_break
                """),
                "items.ruby_pickaxe.mechanics.on_block_break must be a list");
    }

    @Test
    void rejectsEmptyMechanicList() throws IOException {
        assertInvalid(
                validYamlWithMechanics().replace("""
                      on_block_break:
                        - area_break
                """, """
                      on_block_break: []
                """),
                "items.ruby_pickaxe.mechanics.on_block_break must contain at least one mechanic id");
    }

    @Test
    void rejectsMissingRequiredField() throws IOException {
        assertInvalid(validYaml().replace("    material_base: NOTE_BLOCK\n", ""),
                "blocks.ruby_ore.material_base must be a non-empty string");
    }

    @Test
    void keepsSchemaOneWithMiningFields() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMining()).toFile());

        assertTrue(registry.findBlock(new CustomBlockId("ruby_ore")).isPresent());
    }

    @Test
    void loadsMechanicArguments() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMechanicArguments()).toFile());

        var binding = registry.mechanicBindings().bindingsFor(
                new CustomItemId("ruby_pickaxe"),
                MechanicTrigger.ON_BLOCK_BREAK).get(0);
        assertEquals(new MechanicId("vein_miner"), binding.mechanicId());
        assertEquals(64, binding.arguments().get("max_blocks"));
        assertEquals(20, binding.arguments().get("max_depth"));
        assertEquals(true, binding.arguments().get("require_sneak"));
    }

    private String validYamlWithMechanicArguments() {
        return """
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    drops:
                      - item: ruby
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                    mechanics:
                      on_block_break:
                        - id: vein_miner
                          arguments:
                            max_blocks: 64
                            max_depth: 20
                            require_sneak: true
                """;
    }

    @Test
    void itemWithoutDurabilityHasEmptyOptional() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYaml()).toFile());

        var item = registry.findItem(new CustomItemId("ruby_pickaxe")).orElseThrow();
        assertTrue(item.durability().isEmpty());
    }

    @Test
    void rejectsNonPositiveDurabilityMax() throws IOException {
        assertInvalid(
                validYamlWithDurability().replace("max: 500", "max: 0"),
                "items.ruby_pickaxe.durability.max must be greater than zero but was 0");
    }

    @Test
    void rejectsNegativeDamageOnCustomBlockBreak() throws IOException {
        assertInvalid(
                validYamlWithDurability().replace("damage_on_custom_block_break: 1", "damage_on_custom_block_break: -1"),
                "items.ruby_pickaxe.durability.damage_on_custom_block_break must not be negative but was -1");
    }

    @Test
    void acceptsDefaultBreakWhenZeroTrue() throws IOException {
        String yaml = validYamlWithDurability().replace("break_when_zero: true", "");
        DefinitionRegistry registry = loader().load(writeYaml(yaml).toFile());

        var item = registry.findItem(new CustomItemId("ruby_pickaxe")).orElseThrow();
        assertTrue(item.durability().isPresent());
        assertEquals(com.customcontentengine.domain.durability.ToolBreakPolicy.BREAK, item.durability().get().breakPolicy());
    }

    @Test
    void acceptsBreakWhenZeroFalse() throws IOException {
        String yaml = validYamlWithDurability().replace("break_when_zero: true", "break_when_zero: false");
        DefinitionRegistry registry = loader().load(writeYaml(yaml).toFile());

        var item = registry.findItem(new CustomItemId("ruby_pickaxe")).orElseThrow();
        assertTrue(item.durability().isPresent());
        assertEquals(com.customcontentengine.domain.durability.ToolBreakPolicy.PRESERVE, item.durability().get().breakPolicy());
    }

    @Test
    void loadsItemMiningTier() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMiningAndTier()).toFile());

        assertEquals(
                3,
                registry.findItem(new CustomItemId("ruby_pickaxe")).orElseThrow().miningToolTier().orElseThrow().level());
    }

    @Test
    void loadsBlockMiningRequiredTier() throws IOException {
        DefinitionRegistry registry = loader().load(writeYaml(validYamlWithMiningAndTier()).toFile());

        assertEquals(
                2,
                registry.findBlock(new CustomBlockId("ruby_ore")).orElseThrow().miningRequiredTier().orElseThrow().minimumLevel());
    }

    @Test
    void rejectsZeroItemMiningTier() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("tier: 3", "tier: 0"),
                "items.ruby_pickaxe.mining.tier must be greater than zero but was 0");
    }

    @Test
    void rejectsNegativeItemMiningTier() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("tier: 3", "tier: -1"),
                "items.ruby_pickaxe.mining.tier must be greater than zero but was -1");
    }

    @Test
    void rejectsNonIntegerItemMiningTier() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("tier: 3", "tier: 3.5"),
                "items.ruby_pickaxe.mining.tier must be an integer");
    }

    @Test
    void rejectsZeroBlockMiningRequiredTier() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("required_tier: 2", "required_tier: 0"),
                "blocks.ruby_ore.mining.required_tier must be greater than zero but was 0");
    }

    @Test
    void rejectsNegativeBlockMiningRequiredTier() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("required_tier: 2", "required_tier: -1"),
                "blocks.ruby_ore.mining.required_tier must be greater than zero but was -1");
    }

    @Test
    void rejectsNonIntegerBlockMiningRequiredTier() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("required_tier: 2", "required_tier: 2.5"),
                "blocks.ruby_ore.mining.required_tier must be an integer");
    }

    @Test
    void rejectsItemMiningTierThatIsNotAnInteger() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("tier: 3", "tier: foo"),
                "items.ruby_pickaxe.mining.tier must be an integer");
    }

    @Test
    void rejectsBlockMiningRequiredTierThatIsNotAnInteger() throws IOException {
        assertInvalid(
                validYamlWithMiningAndTier().replace("required_tier: 2", "required_tier: bar"),
                "blocks.ruby_ore.mining.required_tier must be an integer");
    }

    private String validYamlWithMiningAndTier() {
        return """
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    mining:
                      hardness: 6.0
                      required_tier: 2
                    drops:
                      - item: ruby
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                    mining:
                      speed: 8.0
                      tier: 3
                """;
    }

    private String validYamlWithDurability() {
        return """
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    drops:
                      - item: ruby
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                    durability:
                      max: 500
                      damage_on_custom_block_break: 1
                      break_when_zero: true
                """;
    }

    private void assertInvalid(String yaml, String expectedMessagePart) throws IOException {
        YamlDefinitionException exception = assertThrows(YamlDefinitionException.class, () -> loader().load(writeYaml(yaml).toFile()));
        assertTrue(exception.getMessage().contains(expectedMessagePart), () -> "Expected message to contain <" + expectedMessagePart + "> but was <" + exception.getMessage() + ">");
    }

    private Path writeYaml(String yaml) throws IOException {
        Path file = tempDir.resolve("definitions.yml");
        Files.writeString(file, yaml);
        return file;
    }

    private YamlDefinitionLoader loader() {
        return new YamlDefinitionLoader(new YamlDefinitionValidator());
    }

    private String validYaml() {
        return """
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    drops:
                      - item: ruby
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                """;
    }

    private String validYamlWithMechanics() {
        return """
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    drops:
                      - item: ruby
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                    mechanics:
                      on_block_break:
                        - area_break
                """;
    }

    private String validYamlWithMining() {
        return """
                schema: 1
                blocks:
                  ruby_ore:
                    numeric_id: 1
                    material_base: NOTE_BLOCK
                    custom_model_data: 1001
                    required_tool: ruby_pickaxe
                    mining:
                      hardness: 6.0
                    drops:
                      - item: ruby
                        amount: 1
                items:
                  ruby_pickaxe:
                    material_base: DIAMOND_PICKAXE
                    custom_model_data: 2001
                    attributes:
                      damage: 5.0
                      speed: 1.2
                      durability: 500
                    mining:
                      speed: 8.0
                """;
    }
}
