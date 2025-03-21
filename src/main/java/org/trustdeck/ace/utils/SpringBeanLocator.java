/*
 * Trust Deck Services
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trustdeck.ace.utils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * This class offers a way to access spring beans that are otherwise unaccessible.
 * 
 * @author Armin Müller
 */
@Component
public class SpringBeanLocator implements ApplicationContextAware {
	
	/** The context of this application: i.e. the registry of all spring beans. */
	private static ApplicationContext context;

	/** This will automatically be called by spring. */
	@Override
	public final void setApplicationContext(ApplicationContext appContext) {
		context = appContext;
	}
	
	/**
	 * Method to get a bean from the spring bean registry.
	 * 
	 * @param <T> the type parameter
	 * @param type the class for which the bean should be retrieved.
	 * @return the bean for the provided class.
	 */
	public static <T> T getBean(Class<T> type) {
		return context.getBean(type);
	}
}