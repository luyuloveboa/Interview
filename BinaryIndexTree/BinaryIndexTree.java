public class BinaryIndexTree {

    /**
     * Start from index+1 if you updating index in original array. Keep adding this value 
     * for next node till you reach outside range of tree
     */
    public void updateBinaryIndexedTree(int binaryIndexedTree[], int val, int index){
        while(index < binaryIndexedTree.length){
            binaryIndexedTree[index] += val;
            index = getNext(index);
        }
    }
    
    /**
     * Start from index+1 if you want prefix sum 0 to index. Keep adding value
     * till you reach 0
     */
    public int getSum(int binaryIndexedTree[], int index){
        index = index + 1;
        int sum = 0;
        while(index > 0){
            sum += binaryIndexedTree[index];
            index = getParent(index);
        }
        return sum;
    }
    
    /**
     * Creating tree is like updating Fenwick tree for every value in array
     */
    public int[] createTree(int input[]){
        int binaryIndexedTree[] = new int[input.length+1];
        for(int i=1; i <= input.length; i++){
            updateBinaryIndexedTree(binaryIndexedTree, input[i-1], i);
        }
        return binaryIndexedTree;
    }
    
    /**
     * To get parent
     * 1) 2's complement to get minus of index
     * 2) AND this with index
     * 3) Subtract that from index
     */
    private int getParent(int index){
        return index - (index & -index);
    }
    
    /**
     * To get next
     * 1) 2's complement of get minus of index
     * 2) AND this with index
     * 3) Add it to index
     */
    private int getNext(int index){
        return index + (index & -index);
    }
    
    public static void main(String args[]){
        int input[] = {1,2,3,4,5,6,7};
        BinaryIndexTree ft = new BinaryIndexTree();
        int binaryIndexedTree[] = ft.createTree(input);
        System.out.println(ft.getSum(binaryIndexedTree, 0));
        System.out.println(ft.getSum(binaryIndexedTree, 1));
        System.out.println(ft.getSum(binaryIndexedTree, 2));
        System.out.println(ft.getSum(binaryIndexedTree, 3));
        System.out.println(ft.getSum(binaryIndexedTree, 4));
        System.out.println(ft.getSum(binaryIndexedTree, 5));
    }
}
