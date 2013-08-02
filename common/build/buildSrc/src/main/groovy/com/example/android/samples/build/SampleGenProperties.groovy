package com.example.android.samples.build

import freemarker.ext.dom.NodeModel
import groovy.transform.Canonical
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileTree

/**
 * Gradle extension that holds properties for sample generation.
 *
 * The sample generator needs a number of properties whose values can be
 * inferred by convention from a smaller number of initial properties.
 * This class defines fields for the initial properties, and getter
 * methods for the inferred properties. It also defines a small number
 * of convenience methods for setting up template-generation tasks.
 */
@Canonical
class SampleGenProperties {
    /**
     * The Gradle project that this extension is being applied to.
     */
    Project project

    /**
     *  Directory where the top-level sample project lives
     */
    def targetProjectDir

    /**
     * Relative path to samples/common directory
     */
    def pathToSamplesCommon

    /**
     * Java package name for the root package of this sample.
     */
     String targetSamplePackage


    String targetCommonSourceDir() {
        return "${targetProjectDir}/${targetSampleModule()}/src/common/java/com/example/android/common"
    }

    /**
     * The name of this sample (and also of the corresponding .iml file)
     */
    String targetSampleName() {
        return project.file(targetProjectDir).getName()
    }

    /**
     * The name of the main module in the sample project
     */
    String targetSampleModule() {
        return "${targetSampleName()}Sample"
    }

    /**
     * The path to the template parameters file
     */
    String templateXml() {
        return "${targetProjectDir}/template-params.xml"
    }

    /**
     * Returns the sample's fully qualified Java package as an OS dependent
     * path fragment
     */
    String targetSamplePackageAsPath() {
        return targetSamplePackage.replaceAll(/\./, File.separator)
    }

    /**
     * Returns the path to the common/build/templates directory
     */
    String templatesRoot() {
        return "${pathToSamplesCommon}/build/templates"
    }


    /**
     * Returns the path to common/src/java
     */
    String commonSourceRoot() {
        return "${pathToSamplesCommon}/src/java/com/example/android/common"
    }

    /**
     * Returns the path to the template include directory
     */
    String templatesInclude() {
        return "${templatesRoot()}/include"
    }

    /**
     * Returns the output file that will be generated for a particular
     * input, by replacing generic pathnames with project-specific pathnames
     * and dropping the .ftl extension from freemarker files.
     *
     * @param relativeInputPath Input file as a relative path from the template directory
     * @return Relative output file path
     */
    String getOutputForInput(String relativeInputPath) {
        String outputPath = relativeInputPath
        outputPath = outputPath.replaceAll('_PROJECT_', targetSampleName())
        outputPath = outputPath.replaceAll('_MODULE_', targetSampleModule())
        outputPath = outputPath.replaceAll('_PACKAGE_', targetSamplePackageAsPath())

        // This is kind of a hack; IntelliJ picks up any and all subdirectories named .idea, so
        // named them ._IDE_ instead. TODO: remove when generating .idea projects is no longer necessary.
        outputPath = outputPath.replaceAll('_IDE_', "idea")
        outputPath = outputPath.replaceAll(/\.ftl$/, '')

        // Any file beginning with a dot won't get picked up, so rename them as necessary here.
        outputPath = outputPath.replaceAll('gitignore', '.gitignore')
        return outputPath
    }

    /**
     * Returns the tree(s) where the templates to be processed live. The template
     * input paths that are passed to
     * {@link SampleGenProperties#getOutputForInput(java.lang.String) getOutputForInput}
     * are relative to the dir element in each tree.
     */
    FileTree[] templates() {
        def result = []
        def xmlFile = project.file(templateXml())
        if (xmlFile.exists()) {
            def xml = new XmlSlurper().parse(xmlFile)
            xml.template.each { template ->
                result.add(project.fileTree(dir: "${templatesRoot()}/${template.@src}"))
            }
        } else {
            result.add(project.fileTree(dir: "${templatesRoot()}/create"))
        }
        return result;
    }

    /**
     * Path(s) of the common directories to copy over to the sample project.
     */
    FileTree[] common() {
        def result = []
        def xmlFile = project.file(templateXml())
        if (xmlFile.exists()) {
            def xml = new XmlSlurper().parse(xmlFile)
            xml.common.each { common ->
                println "Adding common/${common.@src} from ${commonSourceRoot()}"
                result.add(project.fileTree (
                        dir: "${commonSourceRoot()}",
                        include: "${common.@src}/**/*"
                ))
            }
        }
        return result
    }

    /**
     * Returns the hash to supply to the freemarker template processor.
     * This is loaded from the file specified by {@link SampleGenProperties#templateXml()}
     * if such a file exists, or synthesized with some default parameters if it does not.
     * In addition, some data about the current project is added to the "meta" key of the
     * hash.
     *
     * @return The hash to supply to freemarker
     */
    Map templateParams() {
        Map result = new HashMap();

        def xmlFile = project.file(templateXml())
        if (xmlFile.exists()) {
            // Parse the xml into Freemarker's DOM structure
            def params = freemarker.ext.dom.NodeModel.parse(xmlFile)

            // Move to the <sample> node and stuff that in our map
            def sampleNode = (NodeModel)params.exec(['/sample'])
            result.put("sample", sampleNode)
        } else {
            // Fake data for use on creation
            result.put("sample", [
                    name:targetSampleName(),
                    package:targetSamplePackage,
                    minSdk:4
            ])
        }

        // Extra data that some templates find useful
        result.put("meta", [
                root: targetProjectDir,
                module: targetSampleModule(),
                common: pathToSamplesCommon,
        ])
        return result
    }



    /**
     * Generate default values for properties that can be inferred from an existing
     * generated project, unless those properties have already been
     * explicitly specified.
     */
    void getRefreshProperties() {
        if (!this.targetProjectDir) {
            this.targetProjectDir = project.projectDir
        }
        def xmlFile = project.file(templateXml())
        if (xmlFile.exists()) {
            println "Template XML: $xmlFile"
            def xml = new XmlSlurper().parse(xmlFile)
            this.targetSamplePackage = xml.package.toString()
            println "Target Package: $targetSamplePackage"
        }
    }

    /**
     * Generate default values for creation properties, unless those properties
     * have already been explicitly specified. This method will attempt to get
     * these properties interactively from the user if necessary.
     */
    void getCreationProperties() {
        def calledFrom = project.hasProperty('calledFrom') ? new File(project.calledFrom)
                : project.projectDir
        calledFrom = calledFrom.getCanonicalPath()
        println('\n\n\nReady to create project...')

        if (!this.pathToSamplesCommonSet) {
            if (project.hasProperty('pathToSamplesCommon')) {
                this.pathToSamplesCommon = project.pathToSamplesCommon
            } else {
                throw new GradleException ('create task requires project property pathToSamplesCommon')
            }
        }

        if (!this.targetProjectDir) {
            if (project.hasProperty('out')) {
                this.targetProjectDir = project.out
            } else {
                this.targetProjectDir  = System.console().readLine("\noutput directory [$calledFrom]:")
                if (this.targetProjectDir.length() <= 0) {
                    this.targetProjectDir = calledFrom
                }
            }
        }

        if (!this.targetSamplePackage) {
            def defaultPackage = "com.example.android." + this.targetSampleName().toLowerCase()
            this.targetSamplePackage = System.console().readLine("\nsample package name[$defaultPackage]:")
            if (this.targetSamplePackage.length() <= 0) {
                this.targetSamplePackage = defaultPackage
            }
        }
    }

}
