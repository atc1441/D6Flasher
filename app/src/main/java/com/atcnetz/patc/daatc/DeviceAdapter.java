/*
 * Copyright (C) 2013 youten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atcnetz.patc.daatc;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.atcnetz.patc.util.ScannedDevice;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class DeviceAdapter extends ArrayAdapter<ScannedDevice> {
    private static final String PREFIX_RSSI = "RSSI:";
    private List<ScannedDevice> mList;
    private LayoutInflater mInflater;
    private int mResId;
    private String prefixFilter = "";
    private boolean rssiSort = false;

    public DeviceAdapter(Context context, int resId, List<ScannedDevice> objects) {
        super(context, resId, objects);
        mResId = resId;
        mList = objects;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ScannedDevice item = (ScannedDevice) getItem(position);

        if (convertView == null) {
            convertView = mInflater.inflate(mResId, null);
        }
        TextView name = (TextView) convertView.findViewById(R.id.device_name);
        assert item != null;
        name.setText(item.getDisplayName());
        TextView address = (TextView) convertView.findViewById(R.id.device_address);
        address.setText(item.getDevice().getAddress());
        TextView rssi = (TextView) convertView.findViewById(R.id.device_rssi);
        rssi.setText(PREFIX_RSSI + item.getRssi());

        return convertView;
    }

    /**
     * add or update BluetoothDevice
     */
    public void update(BluetoothDevice newDevice, int rssi) {
        if ((newDevice == null) || (newDevice.getAddress() == null)) {
            return;
        }

        boolean contains = false;
        for (ScannedDevice device : mList) {
            if (newDevice.getAddress().equals(device.getDevice().getAddress())) {
                contains = true;
                device.setRssi(rssi);
                break;
            }
        }
        if (!contains) {
            if (prefixFilter.equals("")) {
                mList.add(new ScannedDevice(newDevice, rssi));
            } else {
                if (newDevice.getName() != null && newDevice.getName().toLowerCase().startsWith(prefixFilter.toLowerCase()))
                    mList.add(new ScannedDevice(newDevice, rssi));
            }
        }
        sortByRSSI();
        notifyDataSetChanged();
    }

    public void clear() {

        mList.clear();

        notifyDataSetChanged();
    }

    public void setSortByRSSI(boolean state) {
        if (state) {
            rssiSort = true;
            sortByRSSI();
        } else {
            rssiSort = false;
        }
    }

    public void sortByRSSI() {
        if (!rssiSort) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mList.sort(new Comparator<ScannedDevice>() {
                @Override
                public int compare(ScannedDevice lhs, ScannedDevice rhs) {

                    if (lhs.getRssi() < rhs.getRssi()) {
                        return 1;
                    } else if (lhs.getRssi() > rhs.getRssi()) {
                        return -1;
                    } else {
                        return 0;
                    }

                }
            });
            notifyDataSetChanged();
        }
    }

    public void setNameFilter(String filterName) {
        prefixFilter = filterName;
        if (prefixFilter.equals("")) return;
        //noinspection StatementWithEmptyBody
        while(interateList()){}
        notifyDataSetChanged();
    }

    boolean interateList(){
        for (ScannedDevice device : mList) {
            if (device.getDevice().getName() == null || !device.getDevice().getName().toLowerCase().startsWith(prefixFilter.toLowerCase())) {
                //Remove device here
                mList.remove(device);
                return true;
            }
        }
        return false;
    }
}
