package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.ContentIdea;
import com.finscope.service.research.ContentIdeaService;
import com.finscope.web.request.UpdateContentIdeaStatusRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/content-ideas")
public class ContentIdeaController {
    @Resource
    private ContentIdeaService contentIdeaService;

    /**
     * 查询内容创意列表。
     *
     * @return 内容创意列表。
     */
    @GetMapping
    public List<ContentIdea> list() {
        return contentIdeaService.list();
    }

    /**
     * 分页查询内容创意列表。
     *
     * @param page 页码，从 0 开始。
     * @param pageSize 每页条数，范围为 1 到 100。
     * @return 分页后的内容创意结果，包含记录列表和分页元数据。
     */
    @GetMapping("/paged")
    public PageResponse<ContentIdea> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    "page must be >= 0 and pageSize must be between 1 and 100");
        }
        return contentIdeaService.listPaged(page, pageSize);
    }

    /**
     * 查询内容创意详情。
     *
     * @param id 内容创意 ID。
     * @return 指定内容创意详情。
     */
    @GetMapping("/{id}")
    public ContentIdea detail(@PathVariable Long id) {
        return contentIdeaService.detail(id);
    }

    /**
     * 更新内容创意状态。
     *
     * @param id 内容创意 ID。
     * @param request 状态更新请求，包含目标状态。
     * @return 更新后的内容创意。
     */
    @PostMapping("/{id}/status")
    public ContentIdea updateStatus(@PathVariable Long id, @RequestBody UpdateContentIdeaStatusRequest request) {
        return contentIdeaService.updateStatus(id, request.getStatus());
    }
}
