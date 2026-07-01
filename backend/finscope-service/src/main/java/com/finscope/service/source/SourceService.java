package com.finscope.service.source;

import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class SourceService {
    @Resource
    private SourceRepository sourceRepository;

    public List<Source> list() {
        return sourceRepository.findAll();
    }

    public Source create(Source source) {
        return sourceRepository.save(source);
    }

    public Source update(Long id, Source source) {
        return sourceRepository.update(id, source);
    }

    public void delete(Long id) {
        sourceRepository.delete(id);
    }
}
