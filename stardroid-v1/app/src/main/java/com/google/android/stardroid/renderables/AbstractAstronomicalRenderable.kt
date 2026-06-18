// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.renderables

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import java.util.*

/**
 * Base implementation of the [AstronomicalRenderable] and [Renderable]
 * interfaces.
 *
 * @author Brent Bryan
 */
abstract class AbstractAstronomicalRenderable : AstronomicalRenderable {
    override fun initialize(): Renderable {
        return this
    }

    override fun update(): EnumSet<UpdateType> {
        return EnumSet.noneOf(UpdateType::class.java)
    }

    /** Implementors of this method must implement [.getSearchLocation].  */
    override val names: List<String>
        get() = emptyList()
    override val searchLocation: Vector3
        get() {
            throw UnsupportedOperationException("Should not be called")
        }
    override var isVisible = true
    override val images: List<ImagePrimitive>
        get() = emptyList()
    override val labels: List<TextPrimitive>
        get() = emptyList()
    override val lines: List<LinePrimitive>
        get() = emptyList()
    override val points: List<PointPrimitive>
        get() = emptyList()
}