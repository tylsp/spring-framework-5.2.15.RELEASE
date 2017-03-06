/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.result.method.annotation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import rx.Completable;
import rx.Single;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.io.buffer.support.DataBufferTestUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.core.ResolvableType.forClass;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.web.method.ResolvableMethod.on;

/**
 * Unit tests for {@link ResponseEntityResultHandler}. When adding a test also
 * consider whether the logic under test is in a parent class, then see:
 * <ul>
 * <li>{@code MessageWriterResultHandlerTests},
 * <li>{@code ContentNegotiatingResultHandlerSupportTests}
 * </ul>
 * @author Rossen Stoyanchev
 */
public class ResponseEntityResultHandlerTests {

	private ResponseEntityResultHandler resultHandler;

	private MockServerHttpRequest request;

	private MockServerHttpResponse response;


	@Before
	public void setup() throws Exception {
		this.resultHandler = createHandler();
		initExchange();
	}

	private void initExchange() {
		this.request = MockServerHttpRequest.get("/path").build();
		this.response = new MockServerHttpResponse();
	}


	private ResponseEntityResultHandler createHandler(HttpMessageWriter<?>... writers) {
		List<HttpMessageWriter<?>> writerList;
		if (ObjectUtils.isEmpty(writers)) {
			writerList = new ArrayList<>();
			writerList.add(new EncoderHttpMessageWriter<>(new ByteBufferEncoder()));
			writerList.add(new EncoderHttpMessageWriter<>(new CharSequenceEncoder()));
			writerList.add(new ResourceHttpMessageWriter());
			writerList.add(new EncoderHttpMessageWriter<>(new Jaxb2XmlEncoder()));
			writerList.add(new EncoderHttpMessageWriter<>(new Jackson2JsonEncoder()));
		}
		else {
			writerList = Arrays.asList(writers);
		}
		RequestedContentTypeResolver resolver = new RequestedContentTypeResolverBuilder().build();
		return new ResponseEntityResultHandler(writerList, resolver);
	}


	@Test
	@SuppressWarnings("ConstantConditions")
	public void supports() throws NoSuchMethodException {

		Object value = null;

		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, returnType)));

		returnType = on(TestController.class).resolveReturnType(asyncEntity(Mono.class, String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, returnType)));

		returnType = on(TestController.class).resolveReturnType(asyncEntity(Single.class, String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, returnType)));

		returnType = on(TestController.class).resolveReturnType(asyncEntity(CompletableFuture.class, String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, returnType)));
	}

	@Test
	@SuppressWarnings("ConstantConditions")
	public void doesNotSupport() throws NoSuchMethodException {

		Object value = null;

		MethodParameter returnType = on(TestController.class).resolveReturnType(forClass(String.class));
		assertFalse(this.resultHandler.supports(handlerResult(value, returnType)));

		returnType = on(TestController.class).resolveReturnType(forClass(Completable.class));
		assertFalse(this.resultHandler.supports(handlerResult(value, returnType)));
	}

	@Test
	public void defaultOrder() throws Exception {
		assertEquals(0, this.resultHandler.getOrder());
	}

	@Test
	public void statusCode() throws Exception {
		ResponseEntity<Void> value = ResponseEntity.noContent().build();
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(Void.class));
		HandlerResult result = handlerResult(value, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.NO_CONTENT, this.response.getStatusCode());
		assertEquals(0, this.response.getHeaders().size());
		assertResponseBodyIsEmpty();
	}

	@Test
	public void headers() throws Exception {
		URI location = new URI("/path");
		ResponseEntity<Void> value = ResponseEntity.created(location).build();
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(Void.class));
		HandlerResult result = handlerResult(value, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.CREATED, this.response.getStatusCode());
		assertEquals(1, this.response.getHeaders().size());
		assertEquals(location, this.response.getHeaders().getLocation());
		assertResponseBodyIsEmpty();
	}

	@Test
	public void handleResponseEntityWithNullBody() throws Exception {
		Object returnValue = Mono.just(notFound().build());
		MethodParameter type = on(TestController.class).resolveReturnType(asyncEntity(Mono.class, String.class));
		HandlerResult result = handlerResult(returnValue, type);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.NOT_FOUND, this.response.getStatusCode());
		assertResponseBodyIsEmpty();
	}

	@Test
	public void handleReturnTypes() throws Exception {
		Object returnValue = ok("abc");
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		testHandle(returnValue, returnType);

		returnValue = Mono.just(ok("abc"));
		returnType = on(TestController.class).resolveReturnType(asyncEntity(Mono.class, String.class));
		testHandle(returnValue, returnType);

		returnValue = Mono.just(ok("abc"));
		returnType = on(TestController.class).resolveReturnType(asyncEntity(Single.class, String.class));
		testHandle(returnValue, returnType);

		returnValue = Mono.just(ok("abc"));
		returnType = on(TestController.class).resolveReturnType(asyncEntity(CompletableFuture.class, String.class));
		testHandle(returnValue, returnType);
	}

	@Test
	public void handleReturnValueLastModified() throws Exception {
		Instant currentTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant oneMinAgo = currentTime.minusSeconds(60);
		this.request = MockServerHttpRequest.get("/path").ifModifiedSince(currentTime.toEpochMilli()).build();

		ResponseEntity<String> entity = ok().lastModified(oneMinAgo.toEpochMilli()).body("body");
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		HandlerResult result = handlerResult(entity, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertConditionalResponse(HttpStatus.NOT_MODIFIED, null, null, oneMinAgo);
	}

	@Test
	public void handleReturnValueEtag() throws Exception {
		String etagValue = "\"deadb33f8badf00d\"";
		this.request = MockServerHttpRequest.get("/path").ifNoneMatch(etagValue).build();

		ResponseEntity<String> entity = ok().eTag(etagValue).body("body");
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		HandlerResult result = handlerResult(entity, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertConditionalResponse(HttpStatus.NOT_MODIFIED, null, etagValue, Instant.MIN);
	}

	@Test // SPR-14559
	public void handleReturnValueEtagInvalidIfNoneMatch() throws Exception {
		this.request = MockServerHttpRequest.get("/path").ifNoneMatch("unquoted").build();

		ResponseEntity<String> entity = ok().eTag("\"deadb33f8badf00d\"").body("body");
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		HandlerResult result = handlerResult(entity, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.OK, this.response.getStatusCode());
		assertResponseBody("body");
	}

	@Test
	public void handleReturnValueETagAndLastModified() throws Exception {
		String eTag = "\"deadb33f8badf00d\"";

		Instant currentTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant oneMinAgo = currentTime.minusSeconds(60);

		this.request = MockServerHttpRequest.get("/path")
				.ifNoneMatch(eTag)
				.ifModifiedSince(currentTime.toEpochMilli())
				.build();

		ResponseEntity<String> entity = ok().eTag(eTag).lastModified(oneMinAgo.toEpochMilli()).body("body");
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		HandlerResult result = handlerResult(entity, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertConditionalResponse(HttpStatus.NOT_MODIFIED, null, eTag, oneMinAgo);
	}

	@Test
	public void handleReturnValueChangedETagAndLastModified() throws Exception {
		String etag = "\"deadb33f8badf00d\"";
		String newEtag = "\"changed-etag-value\"";

		Instant currentTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant oneMinAgo = currentTime.minusSeconds(60);

		this.request = MockServerHttpRequest.get("/path")
				.ifNoneMatch(etag)
				.ifModifiedSince(currentTime.toEpochMilli())
				.build();

		ResponseEntity<String> entity = ok().eTag(newEtag).lastModified(oneMinAgo.toEpochMilli()).body("body");
		MethodParameter returnType = on(TestController.class).resolveReturnType(entity(String.class));
		HandlerResult result = handlerResult(entity, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertConditionalResponse(HttpStatus.OK, "body", newEtag, oneMinAgo);
	}

	@Test // SPR-14877
	public void handleMonoWithWildcardBodyType() throws Exception {

		ServerWebExchange exchange = createExchange();
		exchange.getAttributes().put(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE,
				Collections.singleton(MediaType.APPLICATION_JSON));

		HandlerResult result = new HandlerResult(new TestController(), Mono.just(ok().body("body")),
				on(TestController.class).resolveReturnType(forClassWithGenerics(Mono.class, ResponseEntity.class)));

		this.resultHandler.handleResult(exchange, result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.OK, this.response.getStatusCode());
		assertResponseBody("\"body\"");
	}

	@Test // SPR-14877
	public void handleMonoWithWildcardBodyTypeAndNullBody() throws Exception {

		ServerWebExchange exchange = createExchange();
		exchange.getAttributes().put(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE,
				Collections.singleton(MediaType.APPLICATION_JSON));

		MethodParameter returnType = on(TestController.class).resolveReturnType(forClassWithGenerics(Mono.class, ResponseEntity.class));

		HandlerResult result = new HandlerResult(new TestController(), Mono.just(notFound().build()), returnType);

		this.resultHandler.handleResult(exchange, result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.NOT_FOUND, this.response.getStatusCode());
		assertResponseBodyIsEmpty();
	}


	private void testHandle(Object returnValue, MethodParameter returnType) {
		initExchange();

		HandlerResult result = handlerResult(returnValue, returnType);
		this.resultHandler.handleResult(createExchange(), result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.OK, this.response.getStatusCode());
		assertEquals("text/plain;charset=UTF-8", this.response.getHeaders().getFirst("Content-Type"));
		assertResponseBody("abc");
	}

	private DefaultServerWebExchange createExchange() {
		return new DefaultServerWebExchange(this.request, this.response);
	}

	private ResolvableType entity(Class<?> bodyType) {
		return forClassWithGenerics(ResponseEntity.class, forClass(bodyType));
	}

	private ResolvableType asyncEntity(Class<?> asyncType, Class<?> bodyType) {
		return forClassWithGenerics(asyncType, entity(bodyType));
	}

	private HandlerResult handlerResult(Object returnValue, MethodParameter returnType) {
		return new HandlerResult(new TestController(), returnValue, returnType);
	}

	private void assertResponseBody(String responseBody) {
		StepVerifier.create(this.response.getBody())
				.consumeNextWith(buf -> assertEquals(responseBody,
						DataBufferTestUtils.dumpString(buf, StandardCharsets.UTF_8)))
				.expectComplete()
				.verify();
	}

	private void assertResponseBodyIsEmpty() {
		StepVerifier.create(this.response.getBody()).expectComplete().verify();
	}

	private void assertConditionalResponse(HttpStatus status, String body, String etag, Instant lastModified)
			throws Exception {

		assertEquals(status, this.response.getStatusCode());
		if (body != null) {
			assertResponseBody(body);
		}
		else {
			assertResponseBodyIsEmpty();
		}
		if (etag != null) {
			assertEquals(1, this.response.getHeaders().get(HttpHeaders.ETAG).size());
			assertEquals(etag, this.response.getHeaders().getETag());
		}
		if (lastModified.isAfter(Instant.EPOCH)) {
			assertEquals(1, this.response.getHeaders().get(HttpHeaders.LAST_MODIFIED).size());
			assertEquals(lastModified.toEpochMilli(), this.response.getHeaders().getLastModified());
		}
	}


	@SuppressWarnings("unused")
	private static class TestController {

		ResponseEntity<String> responseEntityString() { return null; }

		ResponseEntity<Void> responseEntityVoid() { return null; }

		Mono<ResponseEntity<String>> mono() { return null; }

		Single<ResponseEntity<String>> single() { return null; }

		CompletableFuture<ResponseEntity<String>> completableFuture() { return null; }

		String string() { return null; }

		Completable completable() { return null; }

		Mono<ResponseEntity<?>> monoResponseEntityWildcard() { return null; }

	}

}
