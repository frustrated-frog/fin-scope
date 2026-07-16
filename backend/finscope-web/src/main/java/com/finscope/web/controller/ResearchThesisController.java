package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ThesisFinding;
import com.finscope.service.research.ResearchThesisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import com.finscope.web.response.ResearchThesisDetailResponse;

@RestController
@RequestMapping("/api/research/theses")
public class ResearchThesisController {
    @Resource
    private ResearchThesisService researchThesisService;

    /**
     * 查询研究命题列表。
     *
     * @return 研究命题列表。
     */
    @GetMapping
    public ApiResponse<List<ResearchThesis>> list() {
        return ApiResponses.success(researchThesisService.list());
    }

    /**
     * 创建研究命题。
     *
     * @param thesis 研究命题实体，包含命题内容、状态和相关元数据。
     * @return 新创建的研究命题。
     */
    @PostMapping
    public ApiResponse<ResearchThesis> create(@RequestBody ResearchThesis thesis) {
        return ApiResponses.success(researchThesisService.create(thesis));
    }

    /**
     * 查询研究命题详情。
     *
     * @param id 研究命题 ID。
     * @return 研究命题详情，包含发现、研究运行和输出内容。
     */
    @GetMapping("/{id}")
    public ApiResponse<ResearchThesisDetailResponse> detail(@PathVariable Long id) {
        ResearchThesisService.ThesisDetail detail = researchThesisService.detailWithResearch(id);
        return ApiResponses.success(new ResearchThesisDetailResponse(detail.thesis, detail.findings, detail.runs, detail.outputs));
    }

    /**
     * 更新研究命题。
     *
     * @param id 研究命题 ID。
     * @param thesis 研究命题更新内容。
     * @return 更新后的研究命题。
     */
    @PutMapping("/{id}")
    public ApiResponse<ResearchThesis> update(@PathVariable Long id, @RequestBody ResearchThesis thesis) {
        return ApiResponses.success(researchThesisService.update(id, thesis));
    }

    /**
     * 查询研究命题发现列表。
     *
     * @param id 研究命题 ID。
     * @return 该命题下的研究发现列表。
     */
    @GetMapping("/{id}/findings")
    public ApiResponse<List<ThesisFinding>> findings(@PathVariable Long id) {
        return ApiResponses.success(researchThesisService.findings(id));
    }

    /**
     * 新增研究命题发现。
     *
     * @param id 研究命题 ID。
     * @param finding 待新增的研究发现内容。
     * @return 新创建的研究发现。
     */
    @PostMapping("/{id}/findings")
    public ApiResponse<ThesisFinding> addFinding(@PathVariable Long id, @RequestBody ThesisFinding finding) {
        return ApiResponses.success(researchThesisService.addFinding(id, finding));
    }
}
