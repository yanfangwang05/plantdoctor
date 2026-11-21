package cn.caas.plantdoctor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the diagnostic workflow in the offline (mock) profile.
 *
 * <p>These tests assert that the workflow is wired correctly. They say nothing
 * about recognition quality: the labels come from bundled fixtures.
 */
@SpringBootTest(properties = "plantdoctor.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
class DiagnosisWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * The bytes are irrelevant to the mock provider, which keys on the file name;
     * only the extension has to pass the upload filter.
     */
    private static MockMultipartFile image(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }

    @Test
    void advertisesTenCategories() throws Exception {
        mockMvc.perform(get("/api/crop-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(10));
    }

    @Test
    void diagnosesAnImageAndLinksKnowledge() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(image("wheat_rust.jpg"))
                        .param("cropType", "WHEAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.diagnosis.insectName").value("小麦条锈病"))
                .andExpect(jsonPath("$.data.diagnosis.provider").value("mock"))
                .andExpect(jsonPath("$.data.diseaseData.zhName").value("小麦条锈病"))
                .andExpect(jsonPath("$.data.diseaseData.preventionMethods").isNotEmpty())
                .andExpect(jsonPath("$.data.logId").isNotEmpty());
    }

    @Test
    void routesCornThroughTheDedicatedPath() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(image("corn_leaf_blight.jpg"))
                        .param("cropType", "CORN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.diagnosis.insectName").value("玉米大斑病"))
                .andExpect(jsonPath("$.data.diseaseData.zhName").value("玉米大斑病"));
    }

    @Test
    void linksKnowledgeThroughAnAlias() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(image("corn_borer_alias.jpg"))
                        .param("cropType", "INSECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis.insectName").value("玉米钻心虫"))
                .andExpect(jsonPath("$.data.diseaseData.zhName").value("玉米螟"));
    }

    @Test
    void reportsALabelWithoutKnowledgeRecord() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(image("unknown_lesion.jpg"))
                        .param("cropType", "WHEAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.diagnosis.insectName").value("未收录的叶部斑害"))
                .andExpect(jsonPath("$.data.diseaseData").isEmpty());
    }

    @Test
    void rejectsAnUnknownCategory() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(image("wheat_rust.jpg"))
                        .param("cropType", "NO_SUCH_CATEGORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsANonImageUpload() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain",
                                "not an image".getBytes()))
                        .param("cropType", "WHEAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void looksUpKnowledgeByName() throws Exception {
        mockMvc.perform(get("/api/knowledge").param("name", "稻瘟病"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zhName").value("稻瘟病"))
                .andExpect(jsonPath("$.data.enName").value("Rice blast"));
    }

    @Test
    void reportsAnUnknownKnowledgeName() throws Exception {
        mockMvc.perform(get("/api/knowledge").param("name", "不存在的病害"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void logsEveryRequest() throws Exception {
        mockMvc.perform(multipart("/api/diagnosis")
                        .file(image("unknown_lesion.jpg"))
                        .param("cropType", "WHEAT"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/diagnosis/logs").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].logId").isNotEmpty());
    }
}
