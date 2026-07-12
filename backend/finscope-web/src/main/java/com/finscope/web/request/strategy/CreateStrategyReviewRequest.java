package com.finscope.web.request.strategy;
import lombok.Data;
import java.time.LocalDate;
@Data public class CreateStrategyReviewRequest { private LocalDate reviewDate; private String facts; private String reasoning; private String nextAction; }
