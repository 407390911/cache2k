package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
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
 * #L%
 */

/**
 * A data structure to retain all known type information from the key and value types, including generic
 * parameters within the cache configuration. A caching application typically constructs a type descriptor
 * with the use of a {@link CacheTypeCapture}.
 *
 * <p>While the type descriptor contains implementation classes, interface consumers must not rely on the
 * implementation types.
 *
 * @see CacheTypeCapture
 */
public interface CacheType<T> {

  /** The used prefix for the toString() output. */
  String DESCRIPTOR_TO_STRING_PREFIX = "CacheTypeDescriptor#";

  /** Class type if not an array. */
  Class<T> getType();

  /**
   * This ia a parameterized type and the concrete types are known.
   * {@link #getTypeArguments()} returns the the arguments.
   */
  boolean hasTypeArguments();

  /* This type is an array. */
  boolean isArray();

  /** The component type in case of an array */
  CacheType getComponentType();

  /** Known type arguments, if the type is a parameterized type. */
  CacheType[] getTypeArguments();

  /** Java language compatible type name */
  String getTypeName();

  /**
   * Return a serializable version of this type descriptor.
   */
  CacheType getBeanRepresentation();

}
