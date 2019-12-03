/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.docs;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;

public abstract class ReleaseNotes {
    public abstract RegularFileProperty getMarkdownFile();
    public abstract RegularFileProperty getBaseCssFile();
    public abstract RegularFileProperty getReleaseNotesCssFile();
    public abstract RegularFileProperty getReleaseNotesJsFile();
    public abstract ConfigurableFileCollection getJquery();

    public abstract RegularFileProperty getRenderedDocumentation();
}
