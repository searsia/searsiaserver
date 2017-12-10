/*
 * Copyright 2016 Searsia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.searsia.engine;

/**
 * A Searsia Search Exception
 */
public class SearchException extends Exception {

	private static final long serialVersionUID = -7429746644586456271L;

	public SearchException(Exception e) {
		super(e);
	}

	public SearchException(String message) {
		super(message);
	}
	
	@Override
	public String getMessage() {
		String message = super.getMessage();
		message = message.replaceAll("^[A-Za-z\\.]*\\.", ""); // removes Java package names
		message = message.replaceAll(":? ?https?:[^ ]+", ""); // removes URLs (which may contain API keys)
        return message;
	}

	@Override
	public String getLocalizedMessage() { // misusing Localization for full error message
		return super.getMessage();
	}

}
