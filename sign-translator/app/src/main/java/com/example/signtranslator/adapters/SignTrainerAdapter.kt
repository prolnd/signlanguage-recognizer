package com.example.signtranslator.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.signtranslator.R
import com.example.signtranslator.models.SignLetter

/**
 * Adapter for displaying ASL sign reference cards in practice mode.
 * Shows sign images with corresponding letters for training purposes.
 */
class SignTrainerAdapter : RecyclerView.Adapter<SignTrainerAdapter.SignLetterViewHolder>() {

    private var letters: List<SignLetter> = emptyList()

    /**
     * Update the list of sign letters to display
     */
    fun updateLetters(newLetters: List<SignLetter>) {
        letters = newLetters
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SignLetterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sign_card, parent, false)
        return SignLetterViewHolder(view)
    }

    override fun onBindViewHolder(holder: SignLetterViewHolder, position: Int) {
        holder.bind(letters[position])
    }

    override fun getItemCount(): Int = letters.size

    /**
     * ViewHolder for individual sign reference cards
     */
    class SignLetterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val signImage: ImageView = itemView.findViewById(R.id.sign_image)
        private val letterText: TextView = itemView.findViewById(R.id.letter_text)

        /**
         * Bind sign letter data to views
         */
        fun bind(signLetter: SignLetter) {
            signImage.setImageResource(signLetter.imageResourceId)
            letterText.text = if (signLetter.letter == ' ') "SPACE" else signLetter.letter.toString()
            signImage.contentDescription = signLetter.description
        }
    }
}