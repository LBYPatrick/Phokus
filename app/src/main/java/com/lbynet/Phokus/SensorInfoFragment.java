package com.lbynet.Phokus;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.lbynet.Phokus.listener.BMSListener;
import com.lbynet.Phokus.listener.RotationListener;
import com.lbynet.Phokus.utils.MathTools;
import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.SysInfo;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorInfoFragment extends Fragment {

    final public static String TAG = SensorInfoFragment.class.getCanonicalName();
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1",
                                ARG_PARAM2 = "param2";
    private BMSListener bms;
    private RotationListener rot;
    private TextView textAzimuth,
                     textPitch,
                     textRoll;
    private View rootView = null;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public SensorInfoFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment SensorInfoFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static SensorInfoFragment newInstance(String param1, String param2) {
        SensorInfoFragment fragment = new SensorInfoFragment();
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

        bms = new BMSListener() {
            @Override
            public boolean onUpdate(Intent intent) {
                return super.onUpdate(intent);
            }
        };

        rot = new RotationListener() {
            @Override
            public boolean onUpdate(float[] data) {

                //TODO: Finish this

                textAzimuth.setText(String.format("%.2f",MathTools.radianToDegrees(data[0])));
                textPitch.setText(String.format("%.2f", MathTools.radianToDegrees(data[1])));
                textRoll.setText(String.format("%.2f", Math.abs(MathTools.radianToDegrees(data[2])) - 90));
                return true;
                //return super.onUpdate(data);
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_sensor_info, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;

        textAzimuth = rootView.findViewById(R.id.data_azimuth);
        textPitch   = rootView.findViewById(R.id.data_pitch);
        textRoll    = rootView.findViewById(R.id.data_roll);
    }

    @Override
    public void onResume() {
        super.onResume();
        SysInfo.addListeners(bms,rot);

        SAL.print(TAG,"Sensor listeners added");
    }

    @Override
    public void onPause() {
        super.onPause();
        SysInfo.removeListeners(bms,rot);
        SAL.print(TAG,"Sensor listeners removed");
    }

    //TODO: Fill stuff in
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}