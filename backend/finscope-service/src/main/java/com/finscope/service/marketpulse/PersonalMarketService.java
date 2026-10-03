package com.finscope.service.marketpulse;

import com.finscope.dao.marketpulse.PersonalMarketRepository;
import com.finscope.domain.marketpulse.PersonalMarketChange;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service
public class PersonalMarketService {
    @Resource
    private PersonalMarketRepository repository;

    public List<PersonalMarketChange> changes(LocalDate date) {
        return repository.findChanges(date);
    }
}
