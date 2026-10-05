package com.finscope.domain.financials;

import com.finscope.common.enums.financials.FinancialInterpretationChapter;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一章研究分析；教学说明与材料缺口由服务端补齐，不作为公司事实。 */
@Data
public class FinancialInterpretationSection {
    private FinancialInterpretationChapter code;
    private String title;
    private String assessment;
    private String confidence;
    private String summary;
    private List<String> refs = new ArrayList<>();
    private List<FinancialInterpretation.Claim> facts = new ArrayList<>();
    private List<FinancialInterpretation.Claim> analysis = new ArrayList<>();
    private List<FinancialInterpretation.Claim> counterEvidence = new ArrayList<>();
    private List<FinancialInterpretation.Claim> watchpoints = new ArrayList<>();
    private List<String> limitations = new ArrayList<>();
    private String learningExplanation;
    private String commonMisreading;
}
