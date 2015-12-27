/**
 * * Created by guro saria on 28-11-2015.
 *
 * class for storing position of racer
 */


import jade.core.AID;

public class Competitor {

    private AID id;
    int[] coordinates = new int[2];
    public Competitor(AID id, int x, int y){
        this.id = id;
        this.coordinates[0] = x;
        this.coordinates[1] = y;
    }

    public void setPos(int x, int y)
    {

        this.coordinates[0] = x;
        this.coordinates[1] = y;
    }

    public int[] getPos() { return coordinates;}

    public AID getID(){
        return this.id;
    }
}
