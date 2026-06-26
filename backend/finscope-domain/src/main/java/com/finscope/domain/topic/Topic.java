package com.finscope.domain.topic;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Topic {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String status = "LEARNING";
    private String markdownPath;
    private String terms;
    private String learningQuestions;
    private int articleCount;
    private int briefCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
