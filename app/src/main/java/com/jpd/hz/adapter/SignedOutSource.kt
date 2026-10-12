package com.jpd.hz.adapter

import com.jpd.hz.adapter.run.SyncPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException
import java.io.InputStream

private const val SIGNED_OUT = "Signed out. Nothing was synced."

/**
 * A connection's source once it has signed out: it offers nothing, so a run that raced the
 * sign-out ends with a message and touches no file.
 */
object SignedOutSource : Source {

    override val signInRefused: Flow<Boolean> = flowOf(false)

    override suspend fun checkAvailability(): Availability = Availability.UNREACHABLE

    override suspend fun catalogue(): CatalogueResult = CatalogueResult.Failure(SIGNED_OUT)

    override suspend fun open(item: SourceItem): InputStream = throw IOException(SIGNED_OUT)

    override suspend fun openGroupImage(group: ChoiceGroup): InputStream? = null

    override fun extras(plan: SyncPlan): List<ExtraFile> = emptyList()
}
