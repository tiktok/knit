package tiktok.knit.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import knit.Component
import knit.Loadable
import knit.Named
import knit.Provides
import knit.di

class MainActivity : Activity() {

    @Component
    val aa = AA()

    @Provides
    fun providesOwner1(): @Named("sss") String {
        return "Hello World"
    }

    @Provides
    fun providesOwner2(): String {
        return "Hello World"
    }

    private val strInjected: String by di

    private val owner: Loadable<String> by di

    private val another: IAnother by di

    private val loadableAnother: Loadable<IAnother> by di

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        setContentView(tv)
        tv.text = """
                another: $another,
                strInjected: $strInjected,
                owner: $owner,
            """.trimIndent()
        owner.load()
        owner.get()
        owner.unload()
    }
}