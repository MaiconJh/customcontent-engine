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
