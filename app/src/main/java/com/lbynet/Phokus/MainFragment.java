package com.lbynet.Phokus;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.lbynet.Phokus.frames.EventListener;
import com.lbynet.Phokus.utils.SAL;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private View rootView = null;
    private boolean isWidescreenButtonBusy = false;

    public MainFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MainFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MainFragment newInstance(String param1, String param2) {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rootView = view;

        CameraControl.initiate(rootView.findViewById(R.id.pv_preview),rootView.findViewById(R.id.pv_preview_w));

        rootView.findViewById(R.id.btn_widescreen).setOnClickListener(this::onWidescreenButtonClicked);
    }

    boolean onWidescreenButtonClicked(View v) {

        SAL.print("Button clicked");

        if(isWidescreenButtonBusy) return true;

        Button button = (Button) v;

        CameraControl.toggleWideScreen(new EventListener() {
                @Override
                public boolean onEventBegan(String extra) {
                    button.setClickable(false);
                    isWidescreenButtonBusy = true;
                    SAL.print("Button Locked");
                    return true;
                }

                @Override
                public boolean onEvenFinished(boolean isSuccess, String extra) {
                    button.setClickable(true);
                    isWidescreenButtonBusy = false;
                    SAL.print("Button Unlocked");
                    return true;
                }
            });

        return true;
    }
}