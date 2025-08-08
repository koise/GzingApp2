package com.example.gzingapp.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.models.EmergencyContact

class EmergencyContactsAdapter(
    private var contacts: MutableList<EmergencyContact> = mutableListOf(),
    private val onEditClick: (EmergencyContact) -> Unit,
    private val onDeleteClick: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvContactName: TextView = itemView.findViewById(R.id.tvContactName)
        val tvContactPhone: TextView = itemView.findViewById(R.id.tvContactPhone)
        val tvContactRelationship: TextView = itemView.findViewById(R.id.tvContactRelationship)
        val btnEditContact: ImageButton = itemView.findViewById(R.id.btnEditContact)
        val btnDeleteContact: ImageButton = itemView.findViewById(R.id.btnDeleteContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        
        holder.tvContactName.text = contact.name
        holder.tvContactPhone.text = contact.getFormattedPhoneNumber()
        holder.tvContactRelationship.text = contact.relationship

        holder.btnEditContact.setOnClickListener {
            onEditClick(contact)
        }

        holder.btnDeleteContact.setOnClickListener {
            onDeleteClick(contact)
        }
    }

    override fun getItemCount(): Int = contacts.size

    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    fun addContact(contact: EmergencyContact) {
        contacts.add(contact)
        notifyItemInserted(contacts.size - 1)
    }

    fun updateContact(contact: EmergencyContact) {
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contacts[index] = contact
            notifyItemChanged(index)
        }
    }

    fun removeContact(contact: EmergencyContact) {
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contacts.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun isEmpty(): Boolean = contacts.isEmpty()
}