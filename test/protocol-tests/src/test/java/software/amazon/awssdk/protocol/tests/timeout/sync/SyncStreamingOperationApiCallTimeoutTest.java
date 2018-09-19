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

package software.amazon.awssdk.protocol.tests.timeout.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.net.URI;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.protocol.tests.timeout.BaseTimeoutTest.SlowBytesResponseTransformer;
import software.amazon.awssdk.protocol.tests.timeout.BaseTimeoutTest.SlowCustomResponseTransformer;
import software.amazon.awssdk.protocol.tests.timeout.BaseTimeoutTest.SlowFileResponseTransformer;
import software.amazon.awssdk.protocol.tests.timeout.BaseTimeoutTest.SlowInputStreamResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.protocolrestjson.ProtocolRestJsonClient;
import software.amazon.awssdk.services.protocolrestjson.model.StreamingOutputOperationRequest;

/**
 * A set of tests to test ApiCallTimeout for synchronous streaming operations because they are tricky.
 */
public class SyncStreamingOperationApiCallTimeoutTest {

    private static final int TIMEOUT = 1000;
    private static final int DELAY_BEFORE_TIMEOUT = 100;

    @Rule
    public WireMockRule wireMock = new WireMockRule(0);

    private ProtocolRestJsonClient client;

    @Before
    public void setup() {
        client = ProtocolRestJsonClient.builder()
                                       .region(Region.US_WEST_1)
                                       .endpointOverride(URI.create("http://localhost:" + wireMock.port()))
                                       .credentialsProvider(() -> AwsBasicCredentials.create("akid", "skid"))
                                       .overrideConfiguration(b -> b
                                           .apiCallTimeout(Duration.ofMillis(TIMEOUT))
                                           .retryPolicy(RetryPolicy.none()))
                                       .build();
    }

    @Test
    public void streamingOperation_slowFileTransformer_shouldThrowApiCallTimeoutException() {
        stubFor(post(anyUrl())
                    .willReturn(aResponse()
                                    .withStatus(200).withFixedDelay(DELAY_BEFORE_TIMEOUT)));

        assertThatThrownBy(() -> client
            .streamingOutputOperation(
                StreamingOutputOperationRequest.builder().build(), new SlowFileResponseTransformer<>()))
            .isInstanceOf(ApiCallTimeoutException.class);
    }

    @Test
    public void streamingOperation_slowBytesTransformer_shouldThrowApiCallTimeoutException() {
        stubFor(post(anyUrl())
                    .willReturn(aResponse()
                                    .withStatus(200).withFixedDelay(DELAY_BEFORE_TIMEOUT)));

        assertThatThrownBy(() -> client
            .streamingOutputOperation(
                StreamingOutputOperationRequest.builder().build(), new SlowBytesResponseTransformer<>()))
            .isInstanceOf(ApiCallTimeoutException.class);
    }

    @Test
    public void streamingOperation_slowInputTransformer_shouldThrowApiCallTimeoutException() {
        stubFor(post(anyUrl())
                    .willReturn(aResponse()
                                    .withStatus(200).withFixedDelay(DELAY_BEFORE_TIMEOUT)));

        assertThatThrownBy(() -> client
            .streamingOutputOperation(
                StreamingOutputOperationRequest.builder().build(), new SlowInputStreamResponseTransformer<>()))
            .isInstanceOf(ApiCallTimeoutException.class);
    }

    @Test
    public void streamingOperation_slowCustomResponseTransformer_shouldThrowApiCallTimeoutException() {
        stubFor(post(anyUrl())
                    .willReturn(aResponse()
                                    .withStatus(200).withFixedDelay(DELAY_BEFORE_TIMEOUT)));

        assertThatThrownBy(() -> client
            .streamingOutputOperation(
                StreamingOutputOperationRequest.builder().build(), new SlowCustomResponseTransformer()))
            .isInstanceOf(ApiCallTimeoutException.class);
    }
}
