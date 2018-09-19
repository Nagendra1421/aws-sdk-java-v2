/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.internal.http.pipeline.stages;

import static software.amazon.awssdk.core.internal.http.timers.TimerUtils.resolveTimeoutInMillis;
import static software.amazon.awssdk.core.internal.http.timers.TimerUtils.timeSyncTaskIfNeeded;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.internal.http.HttpClientDependencies;
import software.amazon.awssdk.core.internal.http.InterruptMonitor;
import software.amazon.awssdk.core.internal.http.RequestExecutionContext;
import software.amazon.awssdk.core.internal.http.pipeline.RequestPipeline;
import software.amazon.awssdk.core.internal.http.timers.TimeoutTracker;
import software.amazon.awssdk.http.AbortableCallable;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkRequestContext;
import software.amazon.awssdk.utils.Pair;

/**
 * Delegate to the HTTP implementation to make an HTTP request and receive the response.
 */
@SdkInternalApi
public class MakeHttpRequestStage
    implements RequestPipeline<SdkHttpFullRequest, Pair<SdkHttpFullRequest, SdkHttpFullResponse>> {

    private final SdkHttpClient sdkHttpClient;
    private final Duration apiCallAttemptTimeout;
    private final ScheduledExecutorService timeoutExecutor;

    public MakeHttpRequestStage(HttpClientDependencies dependencies) {
        this.sdkHttpClient = dependencies.clientConfiguration().option(SdkClientOption.SYNC_HTTP_CLIENT);
        this.apiCallAttemptTimeout = dependencies.clientConfiguration().option(SdkClientOption.API_CALL_ATTEMPT_TIMEOUT);
        this.timeoutExecutor = dependencies.clientConfiguration().option(SdkClientOption.SCHEDULED_EXECUTOR_SERVICE);
    }

    /**
     * Returns the response from executing one httpClientSettings request; or null for retry.
     */
    public Pair<SdkHttpFullRequest, SdkHttpFullResponse> execute(SdkHttpFullRequest request,
                                                                 RequestExecutionContext context) throws Exception {
        InterruptMonitor.checkInterrupted();
        final SdkHttpFullResponse httpResponse = executeHttpRequest(request, context);
        return Pair.of(request, httpResponse);
    }

    private SdkHttpFullResponse executeHttpRequest(SdkHttpFullRequest request, RequestExecutionContext context) throws Exception {
        final AbortableCallable<SdkHttpFullResponse> requestCallable = sdkHttpClient
            .prepareRequest(request, SdkRequestContext.builder().build());

        long timeoutInMillis = resolveTimeoutInMillis(context.requestConfig()::apiCallAttemptTimeout, apiCallAttemptTimeout);

        TimeoutTracker timeoutTracker = timeSyncTaskIfNeeded(timeoutExecutor, timeoutInMillis, false);
        timeoutTracker.abortable(requestCallable);

        context.apiCallAttemptTimeoutTracker(timeoutTracker);
        context.apiCallTimeoutTracker().abortable(requestCallable);

        try {
            return requestCallable.call();
        } finally {
            timeoutTracker.cancel();
        }
    }
}
