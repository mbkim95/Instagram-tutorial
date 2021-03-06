package com.project.instagram.Navigation

import android.content.Intent
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
import com.google.firebase.firestore.Query
import com.project.instagram.Navigation.model.AlarmDTO
import com.project.instagram.Navigation.model.ContentDTO
import com.project.instagram.Navigation.util.FcmPush
import com.project.instagram.R
import kotlinx.android.synthetic.main.fragment_detail.view.*
import kotlinx.android.synthetic.main.item_detail.view.*

class DetailViewFragment : Fragment() {
    var fragmentView: View? = null
    var firestore: FirebaseFirestore? = null
    var uid: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView =
            LayoutInflater.from(activity).inflate(R.layout.fragment_detail, container, false)
        firestore = FirebaseFirestore.getInstance()
        uid = FirebaseAuth.getInstance().currentUser?.uid

        fragmentView?.detailViewFragment_recyclerView?.adapter = DetailViewRecyclerViewAdapter()
        fragmentView?.detailViewFragment_recyclerView?.layoutManager = LinearLayoutManager(activity)
        return fragmentView
    }

    inner class DetailViewRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var contentDTOs: ArrayList<ContentDTO> = arrayListOf()
        var contentUidList: ArrayList<String> = arrayListOf()

        init {
            firestore?.collection("images")?.orderBy("timestamp", Query.Direction.DESCENDING)
                ?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    contentDTOs.clear()
                    contentUidList.clear()
                    // Sometimes, This code return null or querySnapshot when it signout
                    if (querySnapshot == null) {
                        return@addSnapshotListener
                    }

                    for (snapshot in querySnapshot.documents) {
                        val item = snapshot.toObject(ContentDTO::class.java)
                        contentDTOs.add(item!!)
                        contentUidList.add(snapshot.id)
                    }
                    notifyDataSetChanged()
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_detail, parent, false)
            return CustomViewHolder(view)
        }

        override fun getItemCount(): Int {
            return contentDTOs.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = (holder as CustomViewHolder).itemView

            // UserId
            viewHolder.detailView_item_profile_textView.text = contentDTOs[position].userId

            // User Profile Image
            firestore?.collection("profileImages")?.document(contentDTOs[position].uid!!)?.get()
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val url = task.result!!["image"]
                        Glide.with(holder.itemView.context).load(url)
                            .apply(RequestOptions().circleCrop())
                            .into(viewHolder.detailView_item_profile_image)
                    }
                }

            // Image
            Glide.with(holder.itemView.context).load(contentDTOs[position].imageUrl)
                .into(viewHolder.detailView_item_imageView_content)

            // Explain of content
            viewHolder.detailView_item_explain_textView.text = contentDTOs[position].explain

            // Like count
            viewHolder.detailView_item_favorite_count_textView.text =
                "Likes ${contentDTOs[position].favoriteCount}"

            // Like button event handling
            viewHolder.detailView_item_favorite_imageView.setOnClickListener {
                favoriteEvent(position)
            }

            // This code is when the page is loaded
            if (contentDTOs[position].favorites.containsKey(uid)) {
                viewHolder.detailView_item_favorite_imageView.setImageResource(R.drawable.ic_favorite)
            } else {
                viewHolder.detailView_item_favorite_imageView.setImageResource(R.drawable.ic_favorite_border)
            }

            // This code is when the profile image is clicked
            viewHolder.detailView_item_profile_image.setOnClickListener {
                val fragment = UserFragment()
                val bundle = Bundle()
                bundle.putString("destinationUid", contentDTOs[position].uid)
                bundle.putString("userId", contentDTOs[position].userId)
                fragment.arguments = bundle
                activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.main_content, fragment)
                    ?.commit()
            }
            viewHolder.detailView_item_comment_imageView.setOnClickListener { v ->
                val intent = Intent(v.context, CommentActivity::class.java)
                intent.putExtra("contentUid", contentUidList[position])
                intent.putExtra("destinationUid", contentDTOs[position].uid)
                startActivity(intent)
            }
        }

        fun favoriteEvent(position: Int) {
            val tsDoc = firestore?.collection("images")?.document(contentUidList[position])
            firestore?.runTransaction { transaction ->
                uid = FirebaseAuth.getInstance().currentUser?.uid
                val contentDTO = transaction.get(tsDoc!!).toObject(ContentDTO::class.java)

                if (contentDTO!!.favorites.containsKey(uid)) {
                    // When the favorite button is clicked
                    contentDTO.favoriteCount -= 1
                    contentDTO.favorites.remove(uid)
                } else {
                    // When the favorite button is not clicked
                    contentDTO.favoriteCount += 1
                    contentDTO.favorites[uid!!] = true
                    favoriteAlarm(contentDTOs[position].uid!!)
                }
                transaction.set(tsDoc, contentDTO)
            }
        }
    }

    fun favoriteAlarm(destinationUid: String) {
        val alarmDTO = AlarmDTO()
        alarmDTO.destinationUid = destinationUid
        alarmDTO.userId = FirebaseAuth.getInstance().currentUser?.email
        alarmDTO.uid = FirebaseAuth.getInstance().currentUser?.uid
        alarmDTO.kind = AlarmDTO.ALARM_LIKE
        alarmDTO.timestamp = System.currentTimeMillis()
        FirebaseFirestore.getInstance().collection("alarms").document().set(alarmDTO)

        val message =
            FirebaseAuth.getInstance().currentUser?.email + " " + getString(R.string.alarm_favorite)
        FcmPush.instance.sendMessage(destinationUid, getString(R.string.app_name), message)
    }

    inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
