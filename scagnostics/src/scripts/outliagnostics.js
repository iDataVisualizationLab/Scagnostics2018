import {Delaunator} from "./modules/delaunator";
import {createGraph} from "./modules/kruskal-mst";
import {mst} from "./modules/kruskal-mst";
import {Outlying} from "./modules/outlying";
import {Normalizer} from "./modules/normalizer";
import {LeaderBinner} from "./modules/leaderbinner";
import {Binner} from "./modules/binner";
// import {Binner} from './modules/binner';
(function(window){
    /**
     * initialize a scagnostic object
     * @param inputPoints   {*[][]} set of points from the scatter plot
     * @returns {*[][]}
     */
    window.outliagnostics = function(inputPoints, binType, isNormalized, isBinned) {
        //Clone it to avoid modifying it.
        let points = inputPoints.slice(0);
        let normalizedPoints = points;
        /******This section is about normalizing the data******/
        if(!isNormalized){
            let normalizer = new Normalizer(points);
                normalizedPoints = normalizer.normalizedPoints;
        }
        /******This section is about finding number of bins and binners******/
        let sites = null;
        let bins = null;
        if(!isBinned){//Only do the binning if needed.
            let binSize = null;
            let binner = null;
            let binRadius = 0;
            bins = [];
            do{
                //Start with 40x40 bins, and divided by 2 every time there are more than 250 none empty cells
                binSize = (binSize===null)?40: binSize/2;
                if(binType==="hexagon"){
                    // This section uses hexagon binning
                    let shortDiagonal = 1/binSize;
                    binRadius = Math.sqrt(3)*shortDiagonal/2;
                    binner = new Binner().radius(binRadius).extent([[0, 0], [1, 1]]);//extent from [0, 0] to [1, 1] since we already normalized data.
                    bins = binner.hexbin(normalizedPoints);
                }else if(!binType || binType==="leader"){
                    // This section uses leader binner
                    binRadius = 1/(binSize*2);
                    binner = new LeaderBinner(normalizedPoints, binRadius);
                    bins = binner.leaders;
                }
            }while(bins.length > 250);
            sites = bins.map(d => [d.x, d.y]); //=>sites are the set of centers of all bins
        }else{
            sites = normalizedPoints;
        }

        /******This section is about the triangulating and triangulating results******/
        //Triangulation calculation
        let delaunay = Delaunator.from(sites);
        let triangleCoordinates = delaunay.triangleCoordinates();
        /******This section is about the spanning tree and spanning tree results******/
        //Spanning tree calculation
        let graph = createGraph(triangleCoordinates);
        let mstree = mst(graph);
        /******This section is about the outlying score and outlying score results******/
        let outlying = new Outlying(mstree);
        let outlyingScore = outlying.score();
        outputValue("bins", bins);
        outputValue("outlyingScore", outlyingScore);
        return window.outliagnostics;
        function outputValue(name, value){
            window.outliagnostics[name] = value;
        }
    };

})(window);