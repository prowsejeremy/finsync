package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentHomeBinding
import com.jpd.hz.databinding.ItemHomeCategoryBinding
import com.jpd.hz.home.HomeCategory
import com.jpd.hz.home.HomeLayoutStore
import kotlin.math.roundToInt

// The gap above each category card after the first.
private const val CARD_GAP_DP = 8

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    // The shown categories' cards in Home's order, rebuilt with each new view.
    private var categoryCards: List<Pair<HomeCategory, ItemHomeCategoryBinding>> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSettings.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_settings)
        }
        // Read with every new view: coming back from Settings makes a new one, so changes show.
        buildCategoryCards()
        binding.btnRetry.setOnClickListener { libraryViewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner) { renderSyncRing(SyncDisplay.from(it)) }
        libraryViewModel.homeState.observe(viewLifecycleOwner) { renderLibrary(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        categoryCards = emptyList()
    }

    // One card per shown category, in the saved order (spec "Home").
    private fun buildCategoryCards() {
        val cards = binding.libraryCards
        cards.removeAllViews()
        val gapPx = (CARD_GAP_DP * resources.displayMetrics.density).roundToInt()
        val shown = HomeLayoutStore(requireContext()).load().visible
        categoryCards = shown.mapIndexed { index, category ->
            val card = ItemHomeCategoryBinding.inflate(layoutInflater, cards, false)
            val info = category.info
            card.tvTitle.setText(info.titleRes)
            card.ivIcon.showCategoryIcon(category)
            card.root.setOnClickListener { navigateSafely(R.id.homeFragment, info.actionId) }
            if (index > 0) {
                (card.root.layoutParams as ViewGroup.MarginLayoutParams).topMargin = gapPx
            }
            cards.addView(card.root)
            category to card
        }
    }

    private fun renderSyncRing(display: SyncDisplay) {
        if (display.status == SyncDisplay.Status.SYNCING) {
            binding.syncRing.showSyncProgress(display.progress)
        } else {
            binding.syncRing.hideSyncProgress()
        }
    }

    private fun renderLibrary(state: HomeLibraryState) {
        val ready = state as? HomeLibraryState.Ready
        val folder = libraryViewModel.folderLabel()
        binding.libraryCards.visibility = if (ready != null) View.VISIBLE else View.GONE
        // Below the cards: the folder couldn't be read, or holds nothing hz plays (spec).
        val note = when {
            ready == null -> null
            ready.scanFailed -> getString(R.string.home_library_unreadable, folder)
            ready.isEmpty -> getString(R.string.home_library_empty, folder)
            else -> null
        }
        binding.tvLibraryNote.text = note
        binding.tvLibraryNote.visibility = if (note != null) View.VISIBLE else View.GONE
        binding.libraryStatus.visibility = if (ready == null) View.VISIBLE else View.GONE
        val failed = state == HomeLibraryState.Failed
        binding.btnRetry.visibility = if (failed) View.VISIBLE else View.GONE
        binding.tvLibraryStatus.text = if (failed) {
            getString(R.string.home_library_unreadable, folder)
        } else {
            getString(R.string.home_library_building)
        }
        if (ready == null) return
        categoryCards.forEach { (category, card) ->
            card.tvCount.text = category.info.count(ready).toString()
        }
    }
}
