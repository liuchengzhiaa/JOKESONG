package com.example.asmrplayer.core.model

/** A deterministic, allocation-light natural filename comparator. */
object NaturalNameComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0

        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]

            if (leftChar.isDigit() && rightChar.isDigit()) {
                val numberComparison = compareNumberRuns(left, leftIndex, right, rightIndex)
                if (numberComparison.result != 0) return numberComparison.result
                leftIndex = numberComparison.leftEnd
                rightIndex = numberComparison.rightEnd
                continue
            }

            val characterComparison = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
            if (characterComparison != 0) return characterComparison
            leftIndex++
            rightIndex++
        }

        return left.length.compareTo(right.length).takeIf { it != 0 }
            ?: left.compareTo(right)
    }

    private fun compareNumberRuns(
        left: String,
        leftStart: Int,
        right: String,
        rightStart: Int,
    ): NumberRunComparison {
        val leftEnd = left.indexAfterDigits(leftStart)
        val rightEnd = right.indexAfterDigits(rightStart)
        val leftSignificant = left.indexAfterLeadingZeroes(leftStart, leftEnd)
        val rightSignificant = right.indexAfterLeadingZeroes(rightStart, rightEnd)
        val leftLength = leftEnd - leftSignificant
        val rightLength = rightEnd - rightSignificant

        if (leftLength != rightLength) {
            return NumberRunComparison(leftLength.compareTo(rightLength), leftEnd, rightEnd)
        }

        for (offset in 0 until leftLength) {
            val comparison = left[leftSignificant + offset].compareTo(right[rightSignificant + offset])
            if (comparison != 0) {
                return NumberRunComparison(comparison, leftEnd, rightEnd)
            }
        }

        val runLengthComparison = (leftEnd - leftStart).compareTo(rightEnd - rightStart)
        return NumberRunComparison(runLengthComparison, leftEnd, rightEnd)
    }

    private fun String.indexAfterDigits(start: Int): Int {
        var index = start
        while (index < length && this[index].isDigit()) index++
        return index
    }

    private fun String.indexAfterLeadingZeroes(start: Int, end: Int): Int {
        var index = start
        while (index < end - 1 && this[index] == '0') index++
        return index
    }

    private data class NumberRunComparison(
        val result: Int,
        val leftEnd: Int,
        val rightEnd: Int,
    )
}
