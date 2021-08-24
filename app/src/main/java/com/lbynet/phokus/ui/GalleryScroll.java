package com.lbynet.phokus.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.lbynet.phokus.R;

import org.jetbrains.annotations.NotNull;

//TODO: Finish This
public class GalleryScroll extends Fragment {

    private View rootView = null;
    private RecyclerView recyclerGallery = null;
    private GalleryScrollAdapter galleryScrollAdapter = new GalleryScrollAdapter();

    public GalleryScroll() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_gallery_scroll, container, false);
        // Inflate the layout for this fragment
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull @NotNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        recyclerGallery = rootView.findViewById(R.id.rv_gallery);

        recyclerGallery.setLayoutManager(new GridLayoutManager(requireContext(),5));

        recyclerGallery.setAdapter(galleryScrollAdapter);

    }
}