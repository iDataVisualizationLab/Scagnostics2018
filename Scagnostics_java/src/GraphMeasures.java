/*
 * Graph-theoretic Scagnostic Measures
 *
 * Leland Wilkinson (SPSS, Inc.) and Anushka Anand (University of Illinois at Chicago)
 * This program accompanies the paper by Leland Wilkinson, Anushka Anand, and Robert Grossman
 * called Graph-Theoretic Scagnostics
 * Proceedings of the IEEE Symposium on Information Visualization
 * Minneapolis, MN October 23-25, 2005.
 *
 * Delaunay triangulation adapted with permission from Delaunay.java by Marcus Apel (www.geo.tu-freiberg.de/~apelm/)
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose without fee is hereby granted, provided that this entire notice
 * is included in all copies of any software which is or includes a copy
 * or modification of this software and in all copies of the supporting
 * documentation for such software. Supporting documentation must also include a citation of
 * the abovementioned article, Graph-Theoretic Scagnostics
 * THIS SOFTWARE IS BEING PROVIDED "AS IS", WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTY.  IN PARTICULAR, THE AUTHORS MAKE NO
 * REPRESENTATION OR WARRANTY OF ANY KIND CONCERNING THE MERCHANTABILITY
 * OF THIS SOFTWARE OR ITS FITNESS FOR ANY PARTICULAR PURPOSE.
 */

import java.util.*;
import java.util.List;


class Node {
    int x,y;       // coordinate X,Y
    int count;     // number of points aggregated at this node
    Edge anEdge;     // an edge which starts from this node
    char type;      // 1: inner nodes, 2: on convex hull, 3: ...
    List neighbors; // nearest Delaunay neighbors list
    boolean onMST;
    boolean onHull = false;
    boolean isVisited = false;
    int mstDegree;
    int pointID;
    int nodeID;

    Node(int x, int y, int count, int pointID) {
        this.x = x;
        this.y = y;
        this.count = count;
        anEdge = null;
        neighbors = new ArrayList();
        this.pointID = pointID;
    }

    double distToNode(double px, double py) {
        double dx = px - x;
        double dy = py - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    void setNeighbor(Edge neighbor) {
        neighbors.add(neighbor);
    }

    Iterator getNeighborIterator() {
        return neighbors.iterator();
    }

    Edge shortestEdge(boolean mst) {
        Edge emin = null;
        if (neighbors != null) {
            Iterator it = neighbors.iterator();
            double wmin = Double.MAX_VALUE;
            while (it.hasNext()) {
                Edge e = (Edge) it.next();
                if (mst || !e.otherNode(this).onMST) {
                    double wt = e.weight;
                    if (wt < wmin) {
                        wmin = wt;
                        emin = e;
                    }
                }
            }
        }
        return emin;
    }

    int getMSTChildren(double cutoff, double[] maxLength) {
        int count = 0;
        if (isVisited)
            return count;
        isVisited = true;
        Iterator it = neighbors.iterator();
        while (it.hasNext()) {
            Edge e = (Edge) it.next();
            if (e.onMST) {
                if (e.weight < cutoff) {
                    if (!e.otherNode(this).isVisited) {
                        count += e.otherNode(this).getMSTChildren(cutoff, maxLength);
                        double el = e.weight;
                        if (el > maxLength[0])
                            maxLength[0] = el;
                    }
                }
            }
        }
        count += this.count; // add count for this node
        return count;
    }
}

class Edge {
    Node p1,p2;         // start and end point of the edge
    Edge invE = null;     // inverse edge (p2->p1)
    Edge nextE = null;    // next edge in the triangle in counterclockwise
    Edge nextH = null;    // convex hull link
    Triangle inT = null;      // triangle containing this edge
    double a,b,c;               // line equation parameters. aX+bY+c=0
    double weight;

    boolean onHull = false;
    boolean onMST = false;
    boolean onShape = false;
    boolean onOutlier = false;

    Edge(Node p1, Node p2) {
        update(p1, p2);
    }

    void update(Node p1, Node p2) {
        this.p1 = p1;
        this.p2 = p2;
        a = p2.y - p1.y;
        b = p1.x - p2.x;
        c = p2.x * p1.y - p1.x * p2.y;
        weight = Math.sqrt(a * a + b * b);
        asIndex();
    }

    Edge makeSymm() {
        Edge e = new Edge(p2, p1);
        linkSymm(e);
        return e;
    }

    void linkSymm(Edge e) {
        this.invE = e;
        if (e != null) e.invE = this;
    }

    int onSide(Node nd) {
        double s = a * nd.x + b * nd.y + c;
        if (s > 0.0) return 1;
        if (s < 0.0) return -1;
        return 0;
    }

    void asIndex() {
        p1.anEdge = this;
    }

    Edge mostLeft() {
        Edge ee,e = this;
        while ((ee = e.nextE.nextE.invE) != null && ee != this) e = ee;
        return e.nextE.nextE;
    }

    Edge mostRight() {
        Edge ee,e = this;
        while (e.invE != null && (ee = e.invE.nextE) != this) e = ee;
        return e;
    }

    void deleteSimplex() {
        onShape = false;
        inT.onComplex = false;
        if (invE != null) {
            invE.onShape = false;
            invE.inT.onComplex = false;
        }
    }

    boolean isEqual(Edge e) {
        return (e.p1.x == this.p1.x) && (e.p2.x == this.p2.x) && (e.p1.y == this.p1.y) && (e.p2.y == this.p2.y);
    }

    boolean isEquivalent(Edge e) {
        return ((e.p1.x == this.p1.x) && (e.p2.x == this.p2.x) && (e.p1.y == this.p1.y) && (e.p2.y == this.p2.y)) ||
                ((e.p1.x == this.p2.x) && (e.p1.y == this.p2.y) && (e.p2.x == this.p1.x) && (e.p2.y == this.p1.y));
    }

    Node otherNode(Node n) {
        if (n.equals(p1))
            return p2;
        else
            return p1;
    }

    boolean isNewEdge(Node n) {
        Iterator it = n.getNeighborIterator();
        while (it.hasNext()) {
            Edge e2 = (Edge) it.next();
            if (e2.isEquivalent(this))
                return false;
        }
        return true;
    }

    int getRunts(double[] maxLength) {
        double cutoff = weight;
        double[] maxLength1 = new double[1];
        double[] maxLength2 = new double[1];
        int count1 = p1.getMSTChildren(cutoff, maxLength1);
        int count2 = p2.getMSTChildren(cutoff, maxLength2);
        if (count1 < count2) {
            maxLength[0] = maxLength1[0];
            return count1;
        } else if (count1 == count2) {        // take more tightly clustered child
            if (maxLength1[0] < maxLength2[0])
                maxLength[0] = maxLength1[0];
            else
                maxLength[0] = maxLength2[0];
            return count1;
        } else {
            maxLength[0] = maxLength2[0];
            return count2;
        }
    }
}

class Triangle {
    Edge anEdge;        // an edge of this triangle
    double c_cx;        // center of circle: X
    double c_cy;        // center of circle: Y
    double c_r;         // radius of circle
    boolean onComplex = true;

    Triangle(Edge e1, Edge e2, Edge e3) {
        update(e1, e2, e3);
    }

    Triangle(List edges, Edge e1, Edge e2, Edge e3) {
        update(e1, e2, e3);
        edges.add(e1);
        edges.add(e2);
        edges.add(e3);
    }

    void update(Edge e1, Edge e2, Edge e3) {
        onComplex = true;
        anEdge = e1;
        e1.nextE = e2;
        e2.nextE = e3;
        e3.nextE = e1;
        e1.inT = this;
        e2.inT = this;
        e3.inT = this;
        findCircle();
    }

    boolean inCircle(Node nd) {
        return nd.distToNode(c_cx, c_cy) < c_r;
    }

    void removeEdges(List edges) {
        edges.remove(anEdge);
        edges.remove(anEdge.nextE);
        edges.remove(anEdge.nextE.nextE);
    }

    void findCircle() {
        double x1 = (double) anEdge.p1.x;
        double y1 = (double) anEdge.p1.y;
        double x2 = (double) anEdge.p2.x;
        double y2 = (double) anEdge.p2.y;
        double x3 = (double) anEdge.nextE.p2.x;
        double y3 = (double) anEdge.nextE.p2.y;
        double a = (y2 - y3) * (x2 - x1) - (y2 - y1) * (x2 - x3);
        double a1 = (x1 + x2) * (x2 - x1) + (y2 - y1) * (y1 + y2);
        double a2 = (x2 + x3) * (x2 - x3) + (y2 - y3) * (y2 + y3);
        c_cx = (a1 * (y2 - y3) - a2 * (y2 - y1)) / a / 2;
        c_cy = (a2 * (x2 - x1) - a1 * (x2 - x3)) / a / 2;
        c_r = anEdge.p1.distToNode(c_cx, c_cy);
    }
}

class Triangulation {
    BinnedData bdata;
    List nodes;        // nodes set
    List edges;        // edges set
    List triangles;    // triangles set
    List mstEdges;     // minimum spanning tree set
    List nearestEdges;     // nearest-neighbor edges
    Edge hullStart;    // entering edge of convex hull
    Edge actE;
    Node start, end;    // for graph diameter calculations
    int totalPeeledCount;
    int totalCount;
    double alphaArea = 1, alphaPerimeter = 1, hullArea = 1, hullPerimeter = 1;
    double totalOriginalMSTLengths;
    double totalMSTOutlierLengths;
    double[] sortedOriginalMSTLengths;
    static int numScagnostics = 9;
    public final static int OUTLYING = 0, SKEWED = 1, CLUMPY = 2, SPARSE = 3,
    STRIATED = 4, CONVEX = 5, SKINNY = 6, STRINGY = 7, MONOTONIC = 8;
    public final static String[] scagnosticsLabels = {"Outlying", "Skewed", "Clumpy", "Sparse", "Striated",
                                                      "Convex", "Skinny", "Stringy", "Monotonic"};
    int[] px, py, counts;
    boolean[] isOutlier;
    double FUZZ = .999;

    Triangulation() {
        nodes = new ArrayList();
        edges = new ArrayList();
        triangles = new ArrayList();
        mstEdges = new ArrayList();
    }

    void clear() {
        nodes.clear();
        edges.clear();
        triangles.clear();
        mstEdges.clear();
    }

    public double[] compute(BinnedData bdata, boolean quick) {
        this.bdata = bdata;
        this.px = bdata.getXData();
        this.py = bdata.getYData();
        if (px.length < 2)
            return null;

        findOutliers(bdata);

        computeAlphaGraph();

        if (quick)
            return null;

        computeTotalCount();
        computeAlphaArea();
        computeAlphaPerimeter();
        computeHullArea();
        computeHullPerimeter();
        return computeMeasures();
    }

    private void findOutliers(BinnedData bdata) {
        this.counts = bdata.getCounts();
        isOutlier = new boolean[px.length];
        computeDT(px, py);
        computeMST();
        sortedOriginalMSTLengths = getSortedMSTEdgeLengths();
        double cutoff = computeCutoff(sortedOriginalMSTLengths);
        computeTotalOriginalMSTLengths();
        boolean foundNewOutliers = computeMSTOutliers(cutoff);
        double[] sortedPeeledMSTLengths;
        while (foundNewOutliers) {
            clear();
            computeDT(px, py);
            computeMST();
            sortedPeeledMSTLengths = getSortedMSTEdgeLengths();
            cutoff = computeCutoff(sortedPeeledMSTLengths);
            foundNewOutliers = computeMSTOutliers(cutoff);
        }
    }

    private void computeTotalCount() {
        for (int i = 0; i < counts.length; i++) {
            totalCount += counts[i];
        }
    }

    private double[] computeMeasures() {
        double[] results = new double[numScagnostics];
        // Do not change order of these calls!

        results[OUTLYING] = computeOutlierMeasure();
        results[CLUMPY] = computeClusterMeasure();
        results[SKEWED] = computeMSTEdgeLengthSkewnessMeasure();
        results[CONVEX] = computeConvexityMeasure();
        results[SKINNY] = computeSkinnyMeasure();
        results[STRINGY] = computeStringyMeasure();
        results[STRIATED] = computeStriationMeasure();
        results[SPARSE] = computeSparsenessMeasure();
        results[MONOTONIC] = computeMonotonicityMeasure();
        return results;
    }

    private void computeDT(int[] px, int[] py) {
        totalPeeledCount = 0;
        Random r = new Random(13579);
        for (int i = 0; i < px.length; i++) {
            int x = px[i] + (int) (8 * (r.nextDouble() - .5)); // perturb to prevent singularities
            int y = py[i] + (int) (8 * (r.nextDouble() - .5));
            int count = counts[i];
            if (!isOutlier[i]) {
                insert(x, y, count, i);
                totalPeeledCount += count;
            }
        }
        setNeighbors();
        markHull();
    }

    private void computeMST() {
        if (nodes.size() > 1) {
            List mstNodes = new ArrayList();
            Node mstNode = (Node) nodes.get(0);
            updateMSTNodes(mstNode, mstNodes);
            int count = 1;
            while (count < nodes.size()) {
                Edge addEdge = null;
                double wmin = Double.MAX_VALUE;
                Node nmin = null;
                Iterator mstIterator = mstNodes.iterator();
                while (mstIterator.hasNext()) {
                    mstNode = (Node) mstIterator.next();
                    Edge candidateEdge = mstNode.shortestEdge(false);
                    if (candidateEdge != null) {
                        double wt = candidateEdge.weight;
                        if (wt < wmin) {
                            wmin = wt;
                            nmin = mstNode;
                            addEdge = candidateEdge;
                        }
                    }
                }
                if (addEdge != null) {
                    Node addNode = addEdge.otherNode(nmin);
                    updateMSTNodes(addNode, mstNodes);
                    updateMSTEdges(addEdge, mstEdges);
                }
                count++;
            }
        }
    }

    private boolean computeMSTOutliers(double omega) {
        boolean found = false;
        Iterator it = nodes.iterator();
        while (it.hasNext()) {
            Node n = (Node) it.next();
            Iterator ie = n.neighbors.iterator();
            boolean delete = true;
            while (ie.hasNext()) {
                Edge e = (Edge) ie.next();
                if (e.onMST && e.weight < omega)
                    delete = false;
            }
            if (delete) {
                ie = n.neighbors.iterator();
                double sumlength = 0;
                while (ie.hasNext()) {
                    Edge e = (Edge) ie.next();
                    if (e.onMST && !e.onOutlier) {
                        sumlength += e.weight;
                        e.onOutlier = true;
                    }
                }
                totalMSTOutlierLengths += sumlength;
                isOutlier[n.pointID] = true;
                found = true;
            }
        }
        return found;
    }

    private double computeCutoff(double[] lengths) {
        if (lengths.length == 0) return 0;
        int n50 = lengths.length / 2;
        int n25 = n50 / 2;
        int n75 = n50 + n25;
        return lengths[n75] + 1.5 * (lengths[n75] - lengths[n25]);
    }

    private double computeAlphaValue() {
        int length = sortedOriginalMSTLengths.length;
        int n90 = (9 * length) / 10;
        double alpha = sortedOriginalMSTLengths[n90];
        return Math.min(alpha, 100.);
    }

    private double computeMSTEdgeLengthSkewnessMeasure() {
        if (sortedOriginalMSTLengths.length == 0)
            return 0;
        int n = sortedOriginalMSTLengths.length;
        int n50 = n / 2;
        int n10 = n / 10;
        int n90 = (9 * n) / 10;
        double skewness = (sortedOriginalMSTLengths[n90] - sortedOriginalMSTLengths[n50]) /
                (sortedOriginalMSTLengths[n90] - sortedOriginalMSTLengths[n10]);
        double t = (double) totalCount / 500;
        double correction = .7 + .3 / (1 + t * t);
        return 1 - correction * (1 - skewness);
    }

    private void updateMSTEdges(Edge addEdge, List mstEdges) {
        mstEdges.add(addEdge);
        addEdge.onMST = true;
        addEdge.p1.mstDegree++;
        addEdge.p2.mstDegree++;
    }

    private void updateMSTNodes(Node addNode, List mstNodes) {
        mstNodes.add(addNode);
        addNode.onMST = true;
    }

    private double[] getSortedMSTEdgeLengths() {
        double[] lengths = computeEdgeLengths(mstEdges.iterator(), mstEdges.size());
        Sort.doubleArraySort(lengths, 0, 0);
        return lengths;
    }

    private void computeTotalOriginalMSTLengths() {
        for (int i = 0; i < sortedOriginalMSTLengths.length; i++)
            totalOriginalMSTLengths += sortedOriginalMSTLengths[i];
    }

    private double computeOutlierMeasure() {
        return totalMSTOutlierLengths / totalOriginalMSTLengths;
    }

    private double[] computeEdgeLengths(Iterator graph, int n) {
        double[] lengths = new double[n];
        int i = 0;
        while (graph.hasNext()) {
            Edge e = (Edge) graph.next();
            lengths[i] = e.weight;
            i++;
        }
        return lengths;
    }

    private boolean pointsInCircle(Node n, double xc, double yc, double radius) {
        double r = FUZZ * radius;
        Iterator i = n.neighbors.iterator();
        while (i.hasNext()) {
            Edge e = (Edge) i.next();
            Node no = e.otherNode(n);
            double dist = no.distToNode(xc, yc);
            if (dist < r)
                return true;
        }
        return false;
    }

    private void computeAlphaGraph() { // requires initializing Edge.onShape = false
        boolean deleted;
        double alpha = computeAlphaValue();
        do {
            Iterator i = edges.iterator();
            deleted = false;
            while (i.hasNext()) {
                Edge e = (Edge) i.next();
                if (e.inT.onComplex) {
                    if (alpha < e.weight / 2) {
                        e.inT.onComplex = false;
                        deleted = true;
                    } else {
                        if (e.invE != null)
                            if (e.invE.inT.onComplex)
                                continue;
                        if (!edgeIsExposed(alpha, e)) {
                            e.inT.onComplex = false;
                            deleted = true;
                        }
                    }
                }
            }
        } while (deleted);
        markShape();
    }

    private void markShape() {
        Iterator i = edges.iterator();
        while (i.hasNext()) {
            Edge e = (Edge) i.next();
            e.onShape = false;
            if (e.inT.onComplex) {
                if (e.invE == null) {
                    e.onShape = true;
                } else if (!e.invE.inT.onComplex)
                    e.onShape = true;
            }
        }
    }

    private boolean edgeIsExposed(double alpha, Edge e) {
        double x1 = e.p1.x;
        double x2 = e.p2.x;
        double y1 = e.p1.y;
        double y2 = e.p2.y;
        double xe = (x1 + x2) / 2;
        double ye = (y1 + y2) / 2;
        double d = Math.sqrt(alpha * alpha - e.weight * e.weight / 4);
        double xt = d * (y2 - y1) / e.weight;
        double yt = d * (x2 - x1) / e.weight;
        double xc1 = xe + xt;
        double yc1 = ye - yt;
        double xc2 = xe - xt;
        double yc2 = ye + yt;
        boolean pointsInCircle1 = pointsInCircle(e.p1, xc1, yc1, alpha) ||
                pointsInCircle(e.p2, xc1, yc1, alpha);
        boolean pointsInCircle2 = pointsInCircle(e.p1, xc2, yc2, alpha) ||
                pointsInCircle(e.p2, xc2, yc2, alpha);
        return !(pointsInCircle1 && pointsInCircle2);
    }

    private double computeStringyMeasure() {
        int count1 = 0;
        int count2 = 0;
        Iterator it = nodes.iterator();
        while (it.hasNext()) {
            Node n = (Node) it.next();
            if (n.mstDegree == 1)
                count1++;
            if (n.mstDegree == 2)
                count2++;
        }
        double result = (double) count2 / (double) (nodes.size() - count1);
        return result * result * result;
    }

    private double computeClusterMeasure() {
        Iterator it = mstEdges.iterator();
        double[] maxLength = new double[1];
        double maxValue = 0;
        while (it.hasNext()) {
            Edge e = (Edge) it.next();
            clearVisits();
            e.onMST = false;  // break MST at this edge
            int runts = e.getRunts(maxLength);
            e.onMST = true;   // restore this edge to MST
            if (maxLength[0] > 0) {
                double value = runts * (1 - maxLength[0] / e.weight);
                if (value > maxValue)
                    maxValue = value;
            }
        }
        return 2 * maxValue / totalPeeledCount;
    }

    private void clearVisits() {
        Iterator it = nodes.iterator();
        while (it.hasNext()) {
            Node n = (Node) it.next();
            n.isVisited = false;
        }
    }

    private double computeMonotonicityMeasure() {
        int n = counts.length;
        double[] ax = new double[n];
        double[] ay = new double[n];
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            ax[i] = px[i];
            ay[i] = py[i];
            weights[i] = counts[i];
        }
        double[] rx = Sort.rank(ax, weights);
        double[] ry = Sort.rank(ay, weights);
        double s = computePearson(rx, ry, weights);
        return s * s;
    }

    private double computePearson(double[] x, double[] y, double[] weights) {
        int n = x.length;
        double xmean = 0;
        double ymean = 0;
        double xx = 0;
        double yy = 0;
        double xy = 0;
        double sumwt = 0;
        for (int i = 0; i < n; i++) {
            double wt = weights[i];
            if (wt > 0 && !isOutlier[i]) {
                sumwt += wt;
                xx += (x[i] - xmean) * wt * (x[i] - xmean);
                yy += (y[i] - ymean) * wt * (y[i] - ymean);
                xy += (x[i] - xmean) * wt * (y[i] - ymean);
                xmean += (x[i] - xmean) * wt / sumwt;
                ymean += (y[i] - ymean) * wt / sumwt;
            }
        }
        xy = xy / Math.sqrt(xx * yy);
        return xy;
    }

    private double computeSparsenessMeasure() {
        int n = sortedOriginalMSTLengths.length;
        int n90 = (9 * n) / 10;
        double sparse = Math.min(sortedOriginalMSTLengths[n90] / 1000, 1);
        double t = (double) totalCount / 500;
        double correction = .7 + .3 / (1 + t * t);
        return correction * sparse;
    }

    private double computeStriationMeasure() {
        double numEdges = 0;
        Iterator it = mstEdges.iterator();
        while (it.hasNext()) {
            Edge e = (Edge) it.next();
            Node n1 = e.p1;
            Node n2 = e.p2;
            if (n1.mstDegree == 2 && n2.mstDegree == 2) {
                Edge e1 = getAdjacentMSTEdge(n1, e);
                Edge e2 = getAdjacentMSTEdge(n2, e);
                if (cosineOfAdjacentEdges(e, e1, n1) < -.7 && cosineOfAdjacentEdges(e, e2, n2) < -.7)
                    numEdges++;
            }
        }
        return numEdges / (double) mstEdges.size();
    }

    private Edge getAdjacentMSTEdge(Node n, Edge e) {
        Iterator nt = n.neighbors.iterator();
        while (nt.hasNext()) {
            Edge et = (Edge) nt.next();
            if (et.onMST && !e.equals(et)) {
                return et;
            }
        }
        return null;
    }

    private double cosineOfAdjacentEdges(Edge e1, Edge e2, Node n) {
        double v1x = e1.otherNode(n).x - n.x;
        double v1y = e1.otherNode(n).y - n.y;
        double v2x = e2.otherNode(n).x - n.x;
        double v2y = e2.otherNode(n).y - n.y;
        double v1 = Math.sqrt(v1x * v1x + v1y * v1y);
        double v2 = Math.sqrt(v2x * v2x + v2y * v2y);
        v1x = v1x / v1;
        v1y = v1y / v1;
        v2x = v2x / v2;
        v2y = v2y / v2;
        return v1x * v2x + v1y * v2y;
    }

    private double computeConvexityMeasure() {
        if (hullArea == 0) // points in general position
            return 1;
        else {
            double t = (double) totalCount / 500;
            double correction = .7 + .3 / (1 + t * t);
            double convexity = alphaArea / hullArea;
            return correction * convexity;
        }
    }

    private double computeSkinnyMeasure() {
        if (alphaPerimeter > 0)
            return 1 - Math.sqrt(4 * Math.PI * alphaArea) / alphaPerimeter;
        else
            return 1;
    }

    private void computeAlphaArea() {
        double area = 0;
        Iterator tri = triangles.iterator();
        while (tri.hasNext()) {
            Triangle t = (Triangle) tri.next();
            if (t.onComplex) {
                Node p1 = t.anEdge.p1;
                Node p2 = t.anEdge.p2;
                Node p3 = t.anEdge.nextE.p2;
                area += Math.abs(p1.x * p2.y + p1.y * p3.x + p2.x * p3.y
                        - p3.x * p2.y - p3.y * p1.x - p1.y * p2.x);
            }
        }
        alphaArea = area / 2;
    }

    private void computeHullArea() {
        double area = 0.0;
        Iterator tri = triangles.iterator();
        while (tri.hasNext()) {
            Triangle t = (Triangle) tri.next();
            Node p1 = t.anEdge.p1;
            Node p2 = t.anEdge.p2;
            Node p3 = t.anEdge.nextE.p2;
            area += Math.abs(p1.x * p2.y + p1.y * p3.x + p2.x * p3.y
                    - p3.x * p2.y - p3.y * p1.x - p1.y * p2.x);
        }
        hullArea = area / 2.;
    }

    private void computeAlphaPerimeter() {
        double sum = 0;
        Iterator it = edges.iterator();
        while (it.hasNext()) {
            Edge e = (Edge) it.next();
            if (e.onShape) {
                sum += e.weight;
            }
        }
        alphaPerimeter = sum;
    }

    private void computeHullPerimeter() {
        double sum = 0;
        Edge e = hullStart;
        do {
            sum += e.p1.distToNode(e.p2.x, e.p2.y);
            e = e.nextH;
        } while (!e.isEqual(hullStart));
        hullPerimeter = sum;
    }

    private void setNeighbors() {
        Iterator it = edges.iterator();
        while (it.hasNext()) {
            Edge e = (Edge) it.next();
            if (e.isNewEdge(e.p1))
                e.p1.setNeighbor(e);
            if (e.isNewEdge(e.p2))
                e.p2.setNeighbor(e);
        }
    }


    private void insert(int px, int py, int count, int id) {
        int eid;
        Node nd = new Node(px, py, count, id);
        nodes.add(nd);
        if (nodes.size() < 3) return;
        if (nodes.size() == 3)    // create the first triangle
        {
            Node p1 = (Node) nodes.get(0);
            Node p2 = (Node) nodes.get(1);
            Node p3 = (Node) nodes.get(2);
            Edge e1 = new Edge(p1, p2);
            if (e1.onSide(p3) == 0) {
                nodes.remove(nd);
                return;
            }
            if (e1.onSide(p3) == -1)  // right side
            {
                p1 = (Node) nodes.get(1);
                p2 = (Node) nodes.get(0);
                e1.update(p1, p2);
            }
            Edge e2 = new Edge(p2, p3);
            Edge e3 = new Edge(p3, p1);
            e1.nextH = e2;
            e2.nextH = e3;
            e3.nextH = e1;
            hullStart = e1;
            triangles.add(new Triangle(edges, e1, e2, e3));
            return;
        }
        actE = (Edge) edges.get(0);
        if (actE.onSide(nd) == -1) {
            if (actE.invE == null)
                eid = -1;
            else
                eid = searchEdge(actE.invE, nd);
        } else
            eid = searchEdge(actE, nd);
        if (eid == 0) {
            nodes.remove(nd);
            return;
        }
        if (eid > 0)
            expandTri(actE, nd, eid);   // nd is inside or on a triangle
        else
            expandHull(nd);                // nd is outside convex hull
    }

    private void expandTri(Edge e, Node nd, int type) {
        Edge e1 = e;
        Edge e2 = e1.nextE;
        Edge e3 = e2.nextE;
        Node p1 = e1.p1;
        Node p2 = e2.p1;
        Node p3 = e3.p1;
        if (type == 2)    // nd is inside of the triangle
        {
            Edge e10 = new Edge(p1, nd);
            Edge e20 = new Edge(p2, nd);
            Edge e30 = new Edge(p3, nd);
            e.inT.removeEdges(edges);
            triangles.remove(e.inT);     // remove old triangle
            Edge e100 = e10.makeSymm();
            Edge e200 = e20.makeSymm();
            Edge e300 = e30.makeSymm();
            triangles.add(new Triangle(edges, e1, e20, e100));
            triangles.add(new Triangle(edges, e2, e30, e200));
            triangles.add(new Triangle(edges, e3, e10, e300));
            swapTest(e1);   // swap test for the three new triangles
            swapTest(e2);
            swapTest(e3);
        } else           // nd is on the edge e
        {
            Edge e4 = e1.invE;
            if (e4 == null || e4.inT == null)           // one triangle involved
            {
                Edge e30 = new Edge(p3, nd);
                Edge e02 = new Edge(nd, p2);
                Edge e10 = new Edge(p1, nd);
                Edge e03 = e30.makeSymm();
//								shareEdges(e03,e30);
                e10.asIndex();
                e1.mostLeft().nextH = e10;
                e10.nextH = e02;
                e02.nextH = e1.nextH;
                hullStart = e02;
                triangles.remove(e1.inT);  // remove oldtriangle and add two new triangles
                edges.remove(e1);
                edges.add(e10);
                edges.add(e02);
                edges.add(e30);
                edges.add(e03);
                triangles.add(new Triangle(e2, e30, e02));
                triangles.add(new Triangle(e3, e10, e03));
                swapTest(e2);   // swap test for the two new triangles
                swapTest(e3);
                swapTest(e30);
            } else         // two triangle involved
            {
                Edge e5 = e4.nextE;
                Edge e6 = e5.nextE;
                Node p4 = e6.p1;
                Edge e10 = new Edge(p1, nd);
                Edge e20 = new Edge(p2, nd);
                Edge e30 = new Edge(p3, nd);
                Edge e40 = new Edge(p4, nd);
                triangles.remove(e.inT);                   // remove oldtriangle
                e.inT.removeEdges(edges);
                triangles.remove(e4.inT);               // remove old triangle
                e4.inT.removeEdges(edges);
                e5.asIndex();   // because e, e4 removed, reset edge sortOrder of node p1 and p2
                e2.asIndex();
                triangles.add(new Triangle(edges, e2, e30, e20.makeSymm()));
                triangles.add(new Triangle(edges, e3, e10, e30.makeSymm()));
                triangles.add(new Triangle(edges, e5, e40, e10.makeSymm()));
                triangles.add(new Triangle(edges, e6, e20, e40.makeSymm()));
                swapTest(e2);   // swap test for the three new triangles
                swapTest(e3);
                swapTest(e5);
                swapTest(e6);
                swapTest(e10);
                swapTest(e20);
                swapTest(e30);
                swapTest(e40);
            }
        }
    }

    private void expandHull(Node nd) {
        Edge e1,e2,e3 = null,enext;
        Edge e = hullStart;
        Edge comedge = null,lastbe = null;
        while (true) {
            enext = e.nextH;
            if (e.onSide(nd) == -1)   // right side
            {
                if (lastbe != null) {
                    e1 = e.makeSymm();
                    e2 = new Edge(e.p1, nd);
                    e3 = new Edge(nd, e.p2);
                    if (comedge == null) {
                        hullStart = lastbe;
                        lastbe.nextH = e2;
                        lastbe = e2;
                    } else
                        comedge.linkSymm(e2);


                    comedge = e3;
                    triangles.add(new Triangle(edges, e1, e2, e3));
                    swapTest(e);
                }
            } else {
                if (comedge != null) break;
                lastbe = e;
            }
            e = enext;
        }

        lastbe.nextH = e3;
        e3.nextH = e;
    }

    private int searchEdge(Edge e, Node nd) {
        int f2,f3;
        Edge e0 = null;
        if ((f2 = e.nextE.onSide(nd)) == -1) {
            if (e.nextE.invE != null)
                return searchEdge(e.nextE.invE, nd);
            else {
                actE = e;
                return -1;
            }
        }
        if (f2 == 0) e0 = e.nextE;
        Edge ee = e.nextE;
        if ((f3 = ee.nextE.onSide(nd)) == -1) {
            if (ee.nextE.invE != null)
                return searchEdge(ee.nextE.invE, nd);
            else {
                actE = ee.nextE;
                return -1;
            }
        }
        if (f3 == 0) e0 = ee.nextE;
        if (e.onSide(nd) == 0) e0 = e;
        if (e0 != null) {
            actE = e0;
            if (e0.nextE.onSide(nd) == 0) {
                actE = e0.nextE;
                return 0;
            }
            if (e0.nextE.nextE.onSide(nd) == 0) return 0;
            return 1;
        }
        actE = ee;
        return 2;
    }

    private void swapTest(Edge e11) {
        Edge e21 = e11.invE;
        if (e21 == null || e21.inT == null) return;
        Edge e12 = e11.nextE;
        Edge e13 = e12.nextE;
        Edge e22 = e21.nextE;
        Edge e23 = e22.nextE;
        if (e11.inT.inCircle(e22.p2) || e21.inT.inCircle(e12.p2)) {
            e11.update(e22.p2, e12.p2);
            e21.update(e12.p2, e22.p2);
            e11.linkSymm(e21);
            e13.inT.update(e13, e22, e11);
            e23.inT.update(e23, e12, e21);
            e12.asIndex();
            e22.asIndex();
            swapTest(e12);
            swapTest(e22);
            swapTest(e13);
            swapTest(e23);
        }
    }

    private void markHull() {
        Edge e = hullStart;
        if (e != null)
            do {
                e.onHull = true;
                e.p1.onHull = true;
                e.p2.onHull = true;
                e = e.nextH;
            } while (!e.isEqual(hullStart));
    }
}
