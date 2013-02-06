/**
 * Copyright (c) 2006-2013, Knut Reinert, Freie Universitaet Berlin
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Freie Universitaet Berlin nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.seqan.knime.gasic.similarity_correction;

import java.util.Arrays;

import org.ejml.simple.SimpleMatrix;

import com.cureos.numerics.Cobyla;
import com.cureos.numerics.CobylaExitStatus;

/**
 * Computes the corrected abundance matrix for a given similarity matrix and
 * read counts.
 * 
 * @author aiche
 */
public class LassoCorrection {

	// initial value taken from scipy version and GASiC source code
	private static final double rhobeg = 1.0;
	private static final double rhoend = 1.0e-10;
	private final int iprint;
	private static final int maxfun = 10000;

	private double[] minSolution;
	private double minObjective;

	public LassoCorrection() {
		this(0);
	}

	public LassoCorrection(int iprint) {
		minObjective = Double.POSITIVE_INFINITY;
		minSolution = null;
		this.iprint = iprint;
	}

	/**
	 * Calculate corrected abundances given a similarity matrix and observations
	 * using optimization.
	 * 
	 * @param similarity
	 *            Matrix with pairwise similarities between species.
	 * @param normalizedReadAbundances
	 *            Vector of read counts per species (normalized).
	 * @return Estimated abundance of each species in the sample.
	 */
	public double[] similarityCorrection(double[][] similarity,
			double[] normalizedReadAbundances) {

		final SimpleMatrix sm = new SimpleMatrix(similarity);
		final SimpleMatrix reads = new SimpleMatrix(sm.numRows(), 1, true,
				normalizedReadAbundances);

		// compute total number of reads
		final int numGenoms = reads.numRows();

		// 1 constraint for each read (non-negative) and total sum <= 1
		final int numConstraints = numGenoms + 1;

		// normalize reads by its total number

		double[][] initialValues = getInitialValues(numGenoms);

		for (double[] initialValue : initialValues) {
			// solve the lasso problem
			CobylaObjective calcfc = new CobylaObjective(sm, reads);

			// do the actual optimization
			CobylaExitStatus stat = Cobyla.FindMinimum(calcfc, numGenoms,
					numConstraints, initialValue, rhobeg, rhoend, iprint,
					maxfun);

			switch (stat) {
			case DivergingRoundingErrors:
				System.err
						.println("Cobyla exited with DivergingRoundingErrors");
				break;
			case MaxIterationsReached:
				System.err.println("Cobyla exited with MaxIterationsReached");
				break;
			default:
				break;
			}

			updateResult(calcfc, initialValue);
		}

		return minSolution;
	}

	private synchronized void updateResult(CobylaObjective cobylaObjective,
			double[] currentSolution) {
		double currentObjective = cobylaObjective
				.computeObjectiveValue(currentSolution);
		if (currentObjective < minObjective) {
			minObjective = currentObjective;
			minSolution = currentSolution;
		}
	}

	private double[][] getInitialValues(final int numGenoms) {
		int possibleEquals = (int) Math.floor((1 / numGenoms) / 0.1);

		double[][] initialValues = new double[(3 * numGenoms) + 1
				+ possibleEquals][];

		double lowStart = 0.1 / (numGenoms - 1);
		for (int i = 0; i < numGenoms; ++i) {
			initialValues[i] = new double[numGenoms];
			Arrays.fill(initialValues[i], lowStart);
			initialValues[i][i] = 0.9;
		}

		lowStart = 0.2 / (numGenoms - 1);
		for (int i = 0; i < numGenoms; ++i) {
			initialValues[numGenoms + i] = new double[numGenoms];
			Arrays.fill(initialValues[numGenoms + i], lowStart);
			initialValues[numGenoms + i][i] = 0.8;
		}

		for (int i = 0; i < numGenoms; ++i) {
			initialValues[(2 * numGenoms) + i] = new double[numGenoms];
			Arrays.fill(initialValues[(2 * numGenoms) + i], 0.01);
			initialValues[(2 * numGenoms) + i][i] = 0.9;
		}

		for (int i = 0; i < possibleEquals; ++i) {
			double startParameter = 1 / numGenoms - (i * 0.1);
			initialValues[(3 * numGenoms) + i] = new double[numGenoms];
			Arrays.fill(initialValues[(3 * numGenoms) + i], startParameter);
		}

		initialValues[initialValues.length - 1] = new double[numGenoms];
		Arrays.fill(initialValues[initialValues.length - 1], 0.5);

		return initialValues;
	}
}
