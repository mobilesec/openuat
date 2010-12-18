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
package net.sf.microlog.util;

/**
 * The PropertiesGetter has all the ways to get at the properties. Any
 * Property-like object should implement this.
 *
 * @author Darius Katz
 */
public interface PropertiesGetter {

    
    /**
	 * Returns the Object to which the specified key is mapped.
     *
     * @param key the key associated to the stored Object
     *
	 * @return the Object to which the key is mapped; null if the key is not
     * mapped to any Object.
	 */
    public Object get(String key);
    
	/**
	 * Returns the String to which the specified key is mapped.
     *
     * @param key the key associated to the stored Object
     *
	 * @return the String to which the key is mapped; null if the key is not
     * mapped to any String.
	 */
    public String getString(String key);
    
	/**
	 * Returns the Object to which the specified key is mapped directly
     * from the default values. Any overridden settings are ignored. Useful
     * if an overridden value is erroneous and a proper value is needed. (The
     * default values are considered to be checked and therefore proper.)
     *
     * @param key the key associated to the stored Object
     *
	 * @return the Object to which the key is mapped; null if the key is not
     * mapped to any Object in this hashtable.
	 */
    public Object getDefaultValue(String key);

}
