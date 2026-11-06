package com.finscope.domain.watchlistresearch;

import com.finscope.common.enums.watchlistresearch.ResearchSignalKind;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchFinding {
    private String key;
    private ResearchSignalKind kind;
    private List<String> codes = new ArrayList<String>();
    private String title;
    private List<String> evidence = new ArrayList<String>();
    /** 仅用于观察项排序，不是投资评级或概率。 */
    private double magnitude;
    private boolean newlyObserved;
}
