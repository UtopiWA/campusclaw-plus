package com.campusclaw.common;

/** 依赖明确拒绝请求时不自动重试；不保存供应商响应或凭据。 */
public class DependencyRejectedException extends DependencyUnavailableException {
    public DependencyRejectedException(String dependency) {
        super(dependency);
    }
}
