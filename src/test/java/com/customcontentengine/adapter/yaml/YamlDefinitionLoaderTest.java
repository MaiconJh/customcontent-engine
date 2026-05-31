package com.customcontentengine.adapter.yaml;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void rejectsMissingRequiredField() throws IOException {
        assertInvalid(validYaml().replace("    material_base: NOTE_BLOCK\n", ""),
                "blocks.ruby_ore.material_base must be a non-empty string");
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
}
