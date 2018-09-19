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

import java.io.IOException;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.internal.Response;
import software.amazon.awssdk.core.internal.http.HttpClientDependencies;
import software.amazon.awssdk.core.internal.http.RequestExecutionContext;
import software.amazon.awssdk.core.internal.http.pipeline.RequestPipeline;
import software.amazon.awssdk.core.internal.http.pipeline.RequestToResponsePipeline;
import software.amazon.awssdk.http.SdkHttpFullRequest;

/**
 * Handles {@link IOException} caused by either ApiCallTimeout or ApiAttemptTimeout for synchronous calls.
 */
@SdkInternalApi
public final class TimeoutExceptionHandlingStage<OutputT> implements RequestToResponsePipeline<OutputT> {

    private final HttpClientDependencies dependencies;
    private final RequestPipeline<SdkHttpFullRequest, Response<OutputT>> requestPipeline;

    public TimeoutExceptionHandlingStage(HttpClientDependencies dependencies, RequestPipeline<SdkHttpFullRequest,
        Response<OutputT>> requestPipeline) {
        this.dependencies = dependencies;
        this.requestPipeline = requestPipeline;
    }

    /**
     * Translate an {@link IOException} caused by timeout based on the following criteria:
     *
     * <ul>
     * <li>If the {@link IOException} is caused by {@link ClientOverrideConfiguration#apiCallTimeout}, translates it to
     * {@link InterruptedException} so it can be handled
     * appropriately in {@link ApiCallTimeoutTrackingStage}. </li>
     * <li>
     * If it is caused by {@link ClientOverrideConfiguration#apiCallAttemptTimeout()}, translates it to
     * {@link ApiCallAttemptTimeoutException} so that it might be retried
     * in {@link RetryableStage}
     * </li>
     * </ul>
     *
     * <p>
     * ApiCallTimeout takes precedence because it is not retryable.
     *
     * @param request the request
     * @param context Context containing both request dependencies, and a container for any mutable state that must be shared
     * between stages.
     * @return the response
     * @throws Exception the translated exception or the original exception
     */
    @Override
    public Response<OutputT> execute(SdkHttpFullRequest request, RequestExecutionContext context) throws Exception {
        try {
            return requestPipeline.execute(request, context);
        } catch (IOException | AbortedException e) {
            if (isCausedByApiCallTimeout(context)) {
                throw new InterruptedException();
            }

            if (isCausedByApiCallAttemptTimeout(context)) {
                throw ApiCallAttemptTimeoutException.create(
                    resolveTimeoutInMillis(context.requestConfig()::apiCallAttemptTimeout,
                                           dependencies.clientConfiguration().option(SdkClientOption.API_CALL_ATTEMPT_TIMEOUT)));
            }

            throw e;
        }
    }

    private boolean isCausedByApiCallAttemptTimeout(RequestExecutionContext context) {
        return context.apiCallAttemptTimeoutTracker().hasExecuted();
    }

    /**
     * Detects if the exception thrown was triggered by the execution timeout.
     *
     * @param context {@link RequestExecutionContext} object.
     * @return True if the exception was caused by the execution timeout, false if not.
     */
    private boolean isCausedByApiCallTimeout(RequestExecutionContext context) {
        return context.apiCallTimeoutTracker().hasExecuted();
    }
}
