/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.serializer;

import java.io.ByteArrayInputStream;
import java.io.NotSerializableException;
import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.core.serializer.support.SerializingConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;


/**
 * @author Gary Russell
 * @author Mark Fisher
 * @since 3.0.5
 */
class SerializationConverterTests {

	@Test
	void serializeAndDeserializeString() {
		var toBytes = new SerializingConverter();
		byte[] bytes = toBytes.convert("Testing");
		DeserializingConverter fromBytes = new DeserializingConverter();
		assertThat(fromBytes.convert(bytes)).isEqualTo("Testing");
	}

	@Test
	void serializeAndDeserializeStringWithCustomSerializer() {
		var toBytes = new SerializingConverter(new DefaultSerializer());
		byte[] bytes = toBytes.convert("Testing");
		DeserializingConverter fromBytes = new DeserializingConverter();
		assertThat(fromBytes.convert(bytes)).isEqualTo("Testing");
	}

	@Test
	void nonSerializableObject() {
		var toBytes = new SerializingConverter();
		assertThatExceptionOfType(SerializationFailedException.class)
				.isThrownBy(() -> toBytes.convert(new Object()))
				.withCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nonSerializableField() {
		var toBytes = new SerializingConverter();
		assertThatExceptionOfType(SerializationFailedException.class)
				.isThrownBy(() -> toBytes.convert(new UnSerializable()))
				.withCauseInstanceOf(NotSerializableException.class);
	}

	@Test
	void deserializationFailure() {
		var fromBytes = new DeserializingConverter();
		assertThatExceptionOfType(SerializationFailedException.class)
				.isThrownBy(() -> fromBytes.convert("Junk".getBytes()));
	}

	@Test
	void deserializationWithClassLoader() {
		var fromBytes = new DeserializingConverter(this.getClass().getClassLoader());
		var toBytes = new SerializingConverter();
		var expected = "SPRING FRAMEWORK";
		assertThat(fromBytes.convert(toBytes.convert(expected))).isEqualTo(expected);
	}

	@Test
	void deserializationWithDeserializer() {
		var fromBytes = new DeserializingConverter(new DefaultDeserializer());
		var toBytes = new SerializingConverter();
		var expected = "SPRING FRAMEWORK";
		assertThat(fromBytes.convert(toBytes.convert(expected))).isEqualTo(expected);
	}

	@Test
	void deserializationIOException() {
		try (var mocked = Mockito.mockConstruction(ConfigurableObjectInputStream.class,
				(mock, context) -> given(mock.readObject()).willThrow(new ClassNotFoundException()))) {
			var defaultSerializer = new DefaultDeserializer(this.getClass().getClassLoader());
			assertThat(mocked).isNotNull();
			assertThatThrownBy(() -> defaultSerializer.deserialize(
					new ByteArrayInputStream("test".getBytes())))
					.hasMessage("Failed to deserialize object type");
		}
	}


	class UnSerializable implements Serializable {

		private static final long serialVersionUID = 1L;

		@SuppressWarnings({"unused", "serial"}) private Object object;
	}

}
