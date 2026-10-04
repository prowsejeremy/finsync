package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentGenresBinding
import com.jpd.finsync.databinding.ItemGenreBinding
import com.jpd.finsync.library.GenreSummary

class GenresFragment : Fragment() {

    private var _binding: FragmentGenresBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GenresViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.tvTitle.setText(R.string.header_genres)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        val adapter = GenresAdapter { genre -> openGenre(genre.genreId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.genres.observe(viewLifecycleOwner) { genres ->
            adapter.submitList(genres)
            binding.tvEmpty.visibility = if (genres.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openGenre(genreId: String) = navigateSafely(
        R.id.genresFragment,
        R.id.action_genres_to_group,
        bundleOf(ARG_GROUP_TYPE to GROUP_TYPE_GENRE, ARG_GROUP_ID to genreId)
    )
}

private class GenresAdapter(
    private val onClick: (GenreSummary) -> Unit
) : ListAdapter<GenreSummary, GenresAdapter.Holder>(GenreDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemGenreBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemGenreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(genre: GenreSummary) {
            val resources = binding.root.resources
            binding.tvName.text = genre.name
            binding.tvCounts.text = joinWithDots(
                listOf(
                    resources.getQuantityString(
                        R.plurals.album_count, genre.albumCount, genre.albumCount
                    ),
                    resources.getQuantityString(
                        R.plurals.song_count, genre.songCount, genre.songCount
                    )
                )
            )
            binding.root.setOnClickListener { onClick(genre) }
        }
    }
}

private object GenreDiff : DiffUtil.ItemCallback<GenreSummary>() {
    override fun areItemsTheSame(oldItem: GenreSummary, newItem: GenreSummary) =
        oldItem.genreId == newItem.genreId

    override fun areContentsTheSame(oldItem: GenreSummary, newItem: GenreSummary) =
        oldItem == newItem
}
