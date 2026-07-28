package com.finscope.rpc.acquisition;

public class DisabledBrowserFetcher implements BrowserFetcher {
    @Override
    public AcquisitionResponse fetch(AcquisitionRequest request) {
        throw new AcquisitionException(AcquisitionErrorType.BROWSER_UNAVAILABLE,
                "页面需要浏览器渲染，但浏览器采集能力未启用", false, null);
    }
}
