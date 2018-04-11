/*
 * Copyright 2002-2018 the original author or authors.
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
package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.function.Function;

import org.eclipse.jetty.client.HttpClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;

/**
 * Jetty ReactiveStreams HttpClient implementation of {@link ClientHttpConnector}.
 *
 * @author Sebastien Deleuze
 * @since 5.1
 */
public class JettyClientHttpConnector implements ClientHttpConnector, SmartLifecycle {

	private final HttpClient httpClient;

	private DataBufferFactory bufferFactory = new DefaultDataBufferFactory();


	/**
	 * Create a Jetty {@link ClientHttpConnector} with the default {@link HttpClient}.
	 */
	public JettyClientHttpConnector() {
		this(new HttpClient());
	}

	/**
	 * Create a Jetty {@link ClientHttpConnector} with the given {@link HttpClient}.
	 */
	public JettyClientHttpConnector(HttpClient httpClient) {
		this.httpClient = httpClient;
	}


	public void setBufferFactory(DataBufferFactory bufferFactory) {
		this.bufferFactory = bufferFactory;
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return this.httpClient.isRunning();
	}

	@Override
	public void start() {
		try {
			// HttpClient is internally synchronized and protected with state checks
			this.httpClient.start();
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public void stop() {
		try {
			this.httpClient.stop();}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		callback.run();
	}

	@Override
	public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
			Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

		if (!uri.isAbsolute()) {
			return Mono.error(new IllegalArgumentException("URI is not absolute: " + uri));
		}

		if (!httpClient.isStarted()) {
			try {
				this.httpClient.start();
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}

		JettyClientHttpRequest clientHttpRequest = new JettyClientHttpRequest(
				httpClient.newRequest(uri).method(method.toString()), bufferFactory);
		return requestCallback.apply(clientHttpRequest).then(
		Mono.from(clientHttpRequest.getReactiveRequest().response((reactiveResponse, contentChunks) -> {
					// Implementation with buffer copy instead of optimized buffer wrapping
					// because the latter hangs since Callback#succeeded doesn't allow
					// releasing the buffer and requesting more data at different times
					// (required for Mono<DataBuffer> for example).
					// See https://github.com/eclipse/jetty.project/issues/2429
					Flux<DataBuffer> content = Flux.from(contentChunks).map(chunk -> {
						DataBuffer buffer = this.bufferFactory.allocateBuffer(chunk.buffer.capacity());
						buffer.write(chunk.buffer);
						chunk.callback.succeeded();
						return buffer;
					});

					return Mono.just(new JettyClientHttpResponse(reactiveResponse, content));
				})));
	}

}
