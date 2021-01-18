import itertools
from pprint import pprint

import numpy as np
import pandas as pd
import seaborn as sns;
import matplotlib.pyplot as plt

profile1 = [[3,2,2,1],[1,3,2,1],[2,1,4,3],[1,3,4,2],[2,3,1],[2,3,1,1]]
profile1_norm = [[3,2,2,1],[1,3,2,1],[2,1,4,3],[1,3,4,2],[2,3,1],[2,3,1,1]]
profile1_w = [0.19,0.28,0.19,0.05,0.19,0.1]

# Normalize and multiply by weights
for n in range(0,len(profile1)):
    profile1_norm[n] = [float(i)/max(profile1[n]) for i in profile1[n]]
    profile1_norm[n] = list(map(lambda x: x*profile1_w[n], profile1_norm[n]))

# Create all possible combinations
res1 = (list(itertools.product(*profile1)))
res1_norm = (list(itertools.product(*profile1_norm)))

# Calculate combination's utility
utils1 = []
for n in res1_norm:
    utils1.append(sum(n))

profile2 = [[1,2,3,4],[3,2,12,1],[3,2,10,1],[12,2,1,13],[3,1,2],[10,3,1,2]]
profile2_norm = [[1,2,3,4],[3,2,12,1],[3,2,10,1],[12,2,1,13],[3,1,2],[10,3,1,2]]
profile2_w = [0.1,0.23,0.11,0.21,0.11,0.25]

for n in range(0,len(profile2)):
    profile2_norm[n] = [float(i)/max(profile2[n]) for i in profile2[n]]
    profile2_norm[n] = list(map(lambda x: x*profile2_w[n], profile2_norm[n]))

res2 = (list(itertools.product(*profile2)))
res2_norm = (list(itertools.product(*profile2_norm)))

utils2 = []
for n in res2_norm:
    utils2.append(sum(n))


# Combine in dictionary and Pandas dataframe
d = {'names':res1, 'Profile 1':utils1, 'Profile 2':utils2}
df = pd.DataFrame(data=d)

def pareto_frontier(Xs, Ys, maxX = True, maxY = True):
    myList = sorted([[Xs[i], Ys[i]] for i in range(len(Xs))], reverse=maxX)
    p_front = [myList[0]]    
    for pair in myList[1:]:
        if maxY: 
            if pair[1] >= p_front[-1][1]:
                p_front.append(pair)
        else:
            if pair[1] <= p_front[-1][1]:
                p_front.append(pair)
    p_frontX = [pair[0] for pair in p_front]
    p_frontY = [pair[1] for pair in p_front]
    return p_frontX, p_frontY

frons = pareto_frontier(df["Profile 1"],df["Profile 2"])

sns.set_style("white")
sns.set_context("paper")

plt.figure(figsize=(10,10))
sns.scatterplot(x="Profile 1", y="Profile 2", data=df, color=".2")
sns.lineplot(x=frons[0], y=frons[1], color="green")
sns.despine()
