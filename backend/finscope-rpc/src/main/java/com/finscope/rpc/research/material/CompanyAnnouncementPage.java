package com.finscope.rpc.research.material;

import com.finscope.domain.research.material.ResearchMaterial;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CompanyAnnouncementPage {
    private List<ResearchMaterial> materials;
    private boolean hasMore;
}
