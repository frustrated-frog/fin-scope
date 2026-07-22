package com.finscope.rpc.quote;

@FunctionalInterface
interface FundDataRequester {
    String get(String url) throws Exception;
}
