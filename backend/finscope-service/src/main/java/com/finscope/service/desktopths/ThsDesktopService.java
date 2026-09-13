package com.finscope.service.desktopths;

import com.finscope.domain.desktopths.ThsSnapshot;
import com.finscope.rpc.desktopths.ThsDesktopClient;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

/** Manual use case: no scheduler, database writes or model calls. */
@Service
public class ThsDesktopService {
    @Resource
    private ThsDesktopClient client;

    public ThsSnapshot capture() {
        return client.capture();
    }
}
