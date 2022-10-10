package ai.metarank.recommend;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Set;

public class BPRRecommender {
    protected float learnRate = 0.01f, maxLearnRate = 1000.0f;
    protected RealMatrix userFactors;
    protected RealMatrix itemFactors;
    protected int numFactors  = 10;
    protected int numIterations = 100;
    protected float initMean = 0.0f;
    protected float initStd = 0.001f;
    protected float regUser  = 0.01f;
    protected float regItem = 0.01f;
    protected SequentialAccessSparseMatrix trainMatrix;
    protected int numUsers;
    protected int numItems;
    protected int numRates;
    protected double maxRate;
    protected double loss, lastLoss = 0.0d;

    /**
     * Minimum rate of rating times
     */
    protected double minRate;

    protected void trainModel() {

        IntOpenHashSet[] userItemsSet = getUserItemsSet(trainMatrix);
        int maxSample = trainMatrix.size();

        for (int iter = 1; iter <= numIterations; iter++) {

            loss = 0.0d;
            for (int sampleCount = 0; sampleCount < maxSample; sampleCount++) {

                // randomly draw (userIdx, posItemIdx, negItemIdx)
                int userIdx, posItemIdx, negItemIdx;
                while (true) {
                    userIdx = Randoms.uniform(numUsers);
                    Set<Integer> itemSet = userItemsSet[userIdx];
                    if (itemSet.size() == 0 || itemSet.size() == numItems)
                        continue;

                    int[] itemIndices = trainMatrix.row(userIdx).getIndices();
                    posItemIdx = itemIndices[Randoms.uniform(itemIndices.length)];
                    do {
                        negItemIdx = Randoms.uniform(numItems);
                    } while (itemSet.contains(negItemIdx));

                    break;
                }

                // update parameters
                double posPredictRating = predict(userIdx, posItemIdx);
                double negPredictRating = predict(userIdx, negItemIdx);
                double diffValue = posPredictRating - negPredictRating;

                double lossValue = -Math.log(Maths.logistic(diffValue));
                loss += lossValue;

                double deriValue = Maths.logistic(-diffValue);

                for (int factorIdx = 0; factorIdx < numFactors; factorIdx++) {
                    double userFactorValue = userFactors.get(userIdx, factorIdx);
                    double posItemFactorValue = itemFactors.get(posItemIdx, factorIdx);
                    double negItemFactorValue = itemFactors.get(negItemIdx, factorIdx);

                    userFactors.plus(userIdx, factorIdx, learnRate * (deriValue * (posItemFactorValue - negItemFactorValue) - regUser * userFactorValue));
                    itemFactors.plus(posItemIdx, factorIdx, learnRate * (deriValue * userFactorValue - regItem * posItemFactorValue));
                    itemFactors.plus(negItemIdx, factorIdx, learnRate * (deriValue * (-userFactorValue) - regItem * negItemFactorValue));

                    loss += regUser * userFactorValue * userFactorValue + regItem * posItemFactorValue * posItemFactorValue + regItem * negItemFactorValue * negItemFactorValue;
                }
            }
            if (isConverged(iter) && earlyStop) {
                break;
            }
            updateLRate(iter);
        }
    }

    private IntOpenHashSet[] getUserItemsSet(SequentialAccessSparseMatrix sparseMatrix) {
        IntOpenHashSet[] tempUserItemsSet = new IntOpenHashSet[numUsers];
        for (int userIdx = 0; userIdx < numUsers; ++userIdx) {
            int[] itemIndices = sparseMatrix.row(userIdx).getIndices();
            IntOpenHashSet itemSet = new IntOpenHashSet(itemIndices.length);
            for(int index = 0; index< itemIndices.length; index++){
                itemSet.add(itemIndices[index]);
            }
            tempUserItemsSet[userIdx] = itemSet;
        }
        return tempUserItemsSet;
    }

    protected double predict(int userIdx, int itemIdx) {
        return userFactors.getRowVector(userIdx).dotProduct(itemFactors.getRowVector(itemIdx));
    }

    protected void updateLRate(int iter) {
        if (learnRate < 0.0) {
            lastLoss = loss;
            return;
        }

        if (isBoldDriver && iter > 1) {
            learnRate = Math.abs(lastLoss) > Math.abs(loss) ? learnRate * 1.05f : learnRate * 0.5f;
        } else if (decay > 0 && decay < 1) {
            learnRate *= decay;
        }

        // limit to max-learn-rate after update
        if (maxLearnRate > 0 && learnRate > maxLearnRate) {
            learnRate = maxLearnRate;
        }
        lastLoss = loss;

    }
    protected boolean isConverged(int iter) throws LibrecException {
        float delta_loss = (float) (lastLoss - loss);

        // print out debug info
        if (verbose) {
            String recName = getClass().getSimpleName();
            String info = recName + " iter " + iter + ": loss = " + loss + ", delta_loss = " + delta_loss;
            LOG.info(info);
        }

        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
//            LOG.error("Loss = NaN or Infinity: current settings does not fit the recommender! Change the settings and try again!");
            throw new LibrecException("Loss = NaN or Infinity: current settings does not fit the recommender! Change the settings and try again!");
        }

        // check if converged

        return Math.abs(delta_loss) < 1e-5;
    }
}
