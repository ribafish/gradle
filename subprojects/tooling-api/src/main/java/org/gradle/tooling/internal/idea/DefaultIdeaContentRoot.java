/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.idea;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * @author: Szczepan Faber, created at: 8/3/11
 */
public class DefaultIdeaContentRoot implements IdeaContentRoot, Serializable {

    File rootDirectory;
    List<IdeaSourceDirectory> sourceDirectories = new LinkedList<IdeaSourceDirectory>();
    List<IdeaSourceDirectory> testDirectories = new LinkedList<IdeaSourceDirectory>();
    List<File> excludeDirectories = new LinkedList<File>();

    public File getRootDirectory() {
        return rootDirectory;
    }

    public DefaultIdeaContentRoot setRootDirectory(File rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    public DomainObjectSet<IdeaSourceDirectory> getSourceDirectories() {
        return new ImmutableDomainObjectSet(sourceDirectories);
    }

    public DefaultIdeaContentRoot setSourceDirectories(List<IdeaSourceDirectory> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
        return this;
    }

    public DomainObjectSet<IdeaSourceDirectory> getTestDirectories() {
        return new ImmutableDomainObjectSet(testDirectories);
    }

    public DefaultIdeaContentRoot setTestDirectories(List<IdeaSourceDirectory> testDirectories) {
        this.testDirectories = testDirectories;
        return this;
    }

    public List<File> getExcludeDirectories() {
        return excludeDirectories;
    }

    public DefaultIdeaContentRoot setExcludeDirectories(List<File> excludeDirectories) {
        this.excludeDirectories = excludeDirectories;
        return this;
    }

    public String toString() {
        return "IdeaContentRoot{"
                + "rootDirectory=" + rootDirectory
                + ", sourceDirectories count=" + sourceDirectories.size()
                + ", testDirectories count=" + testDirectories.size()
                + ", excludeDirectories count=" + excludeDirectories.size()
                + '}';
    }
}