package com.project.instagram.Navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.instagram.Navigation.model.AlarmDTO
import com.project.instagram.R
import kotlinx.android.synthetic.main.fragment_alarm.view.*
import kotlinx.android.synthetic.main.item_comment.view.*

class AlarmFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = LayoutInflater.from(activity).inflate(R.layout.fragment_alarm, container, false)
        view.alarmFragment_recyclerView.adapter = AlarmRecyclerViewAdapter()
        view.alarmFragment_recyclerView.layoutManager = LinearLayoutManager(activity)
        return view
    }

    inner class AlarmRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var alarmDTOs: ArrayList<AlarmDTO> = arrayListOf()

        init {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            FirebaseFirestore.getInstance().collection("alarms")
                .whereEqualTo("destinationUid", uid)
                .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    alarmDTOs.clear()
                    if (querySnapshot == null) {
                        return@addSnapshotListener
                    }

                    for (snapshot in querySnapshot.documents) {
                        alarmDTOs.add(snapshot.toObject(AlarmDTO::class.java)!!)
                    }
                    notifyDataSetChanged()
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return CustomViewHolder(view)
        }

        override fun getItemCount(): Int {
            return alarmDTOs.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView

            FirebaseFirestore.getInstance().collection("profileImages")
                .document(alarmDTOs[position].uid!!).get().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val url = task.result!!["image"]
                        Glide.with(view.context).load(url).apply(RequestOptions().circleCrop())
                            .into(view.commentViewItem_imageView_profile)
                    }
                }

            when (alarmDTOs[position].kind) {
                AlarmDTO.ALARM_LIKE -> {
                    val alarmMessage =
                        alarmDTOs[position].userId + " " + getString(R.string.alarm_favorite)
                    view.commentViewItem_textView_profile.text = alarmMessage
                }

                AlarmDTO.ALARM_COMMENT -> {
                    val alarmMessage =
                        alarmDTOs[position].userId + " " + getString(R.string.alarm_comment)
                    view.commentViewItem_textView_profile.text =
                        alarmMessage + " of " + alarmDTOs[position].message
                }

                AlarmDTO.ALARM_FOLLOW -> {
                    val alarmMessage =
                        alarmDTOs[position].userId + " " + getString(R.string.alarm_follow)
                    view.commentViewItem_textView_profile.text = alarmMessage
                }
            }
            view.commentViewItem_textView_comment.visibility = View.INVISIBLE
        }

        inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
