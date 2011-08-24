/*
 * Copyright 2008 The Microlog project @sourceforge.net
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
package net.sf.microlog.util.properties;

import java.util.Hashtable;

/**
 * An interface that every property source must implement.
 *
 * @author Darius Katz
 */
public interface PropertySource {
    
	/**
	 * Insert the values taken from a property source into the Hashtable.
     * Any previous values with the same key should be overridden/overwritten.
     * 
     * @param properties the Hashtable in which the properties are stored
     *
	 */
    void insertProperties(Hashtable properties);
    
}
