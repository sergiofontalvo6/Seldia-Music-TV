package com.seldia.build

import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class CharsetShimPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
            variant.instrumentation.transformClassesWith(
                CharsetShimFactory::class.java,
                InstrumentationScope.ALL
            ) {}
        }
    }
}
