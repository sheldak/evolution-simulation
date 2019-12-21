package map;

import features.Genome;
import utilities.MapVisualizerX;
import features.Vector2d;
import mapElements.Animal;
import mapElements.Grass;

import java.util.*;

public class WorldMap implements IWorldMap, IPositionChangeObserver {
    private final int width;
    private final int height;

    private final int plantEnergy;

    private WorldBuilder worldBuilder;

    private List<Animal> animals = new ArrayList<>();
    private List<Grass> grasses = new ArrayList<>();
    private Map<Vector2d, List<Object>> elementsMap = new HashMap<>();
    private Map<Genome, Integer> genomes = new HashMap<>();

    private List<Animal> deadAnimals = new ArrayList<>();

    // statistics
    private int currentDay;
    private Map<Integer, Integer> animalsAfterNDays = new HashMap<>();
    private Map<Integer, Integer> grassAfterNDays = new HashMap<>();


    public WorldMap(int width, int height, int plantEnergy, int moveEnergy, int startEnergy) {
        this.width = width;
        this.height = height;

        this.plantEnergy = plantEnergy;
        Animal.moveEnergy = moveEnergy;
        Animal.startEnergy = startEnergy;

        for (int i=0; i<width; i++) {
            for (int j=0; j<height; j++) {
                List<Object> newList = new ArrayList<>();
                elementsMap.put(new Vector2d(i, j), newList);
            }
        }
    }

    public void startWorld(WorldBuilder worldBuilder, double jungleRatio, int numberOfAnimals) {
        this.worldBuilder = worldBuilder;

        worldBuilder.passMapSize(this.width, this.height);
        worldBuilder.placeJungle(jungleRatio);
        worldBuilder.placeInitialAnimals(numberOfAnimals);

        this.currentDay = 0;
    }

    @Override
    public String toString() {
        return this.draw();
    }

    @Override
    public void run() {
        Set <Vector2d> toCheck = new HashSet<>();
        for (Animal animal : this.animals) {
            toCheck.add(animal.move());
        }

        this.checkGrassConsumption(toCheck);
        this.checkReproduction(toCheck);
    }

    @Override
    public boolean isOccupied(Vector2d position) {
        return !this.objectsAt(position).isEmpty();
    }

    @Override
    public List<Object> objectsAt(Vector2d position) {
        return this.elementsMap.get(position);
    }

    @Override
    public void changePosition(Vector2d oldPosition, Vector2d newPosition, Animal animal) {
        this.elementsMap.get(oldPosition).remove(animal);
        this.elementsMap.get(newPosition).add(animal);
    }

    public Vector2d correctDestination(Vector2d destination){
        return new Vector2d((destination.getX() + this.width) % this.width,
                (destination.getY() + this.height) % this.height);
    }

    public void nextDay(){
        this.currentDay += 1;

        for(Animal animal : this.animals) {
            animal.makeOlder();
            animal.saveStatistics();
        }


        this.getRidOfDeadAnimals();

        this.run();

        this.worldBuilder.placeGrassInJungle();
        this.worldBuilder.placeGrassInSavannah();
    }

    public void addAnimal(Animal animal) {
        animal.addObserver(this);
        this.animals.add(animal);
        this.elementsMap.get(animal.getPosition()).add(animal);

        this.addGenome(animal.getGenome());
    }

    public void removeAnimal(Animal animal) {
        animal.kill(currentDay);

        this.elementsMap.get(animal.getPosition()).remove(animal);
        this.animals.remove(animal);

        this.deadAnimals.add(animal);

        this.removeGenome(animal.getGenome());
    }

    public void addGrass(Grass grass) {
        this.grasses.add(grass);
        this.elementsMap.get(grass.getPosition()).add(grass);
    }

    public void addGenome(Genome genome) {
        if (this.genomes.containsKey(genome)) {
            int genomeOccurrence = this.genomes.get(genome);
            this.genomes.remove(genome);
            this.genomes.put(genome, genomeOccurrence + 1);
        }
        else
            this.genomes.put(genome, 1);
    }

    public void removeGenome(Genome genome) {
        if (this.genomes.containsKey(genome)) {
            int genomeOccurrence = this.genomes.get(genome);

            this.genomes.remove(genome);

            if (genomeOccurrence >= 2)
                this.genomes.put(genome, genomeOccurrence - 1);
        }
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getCurrentDay() {
        return this.currentDay;
    }

    // statistics
    public int getNumberOfAnimals() {
        return this.animals.size();
    }

    public int getAmountOfGrass() {
        return this.grasses.size();
    }

    public int getDominantGene() {
        int[] occurrence = new int[8];

        for(int i=0; i<8; i++) occurrence[i] = 0;

        for (Animal animal : this.animals) {
            int[] genome = animal.getGenomeArray();

            for (int j=0; j<32; j++)
                occurrence[genome[j]] += 1;
        }

        int mostPopular = 0;

        for(int i=1; i<8; i++) {
            if (occurrence[i] > occurrence[mostPopular])
                mostPopular = i;
        }

        return mostPopular;
    }

    public Genome getDominantGenome() {
         Genome dominantGenome = null;

         for (Map.Entry<Genome, Integer> genomeEntry : this.genomes.entrySet()) {
             if (dominantGenome == null || genomeEntry.getValue() > this.genomes.get(dominantGenome))
                 dominantGenome = genomeEntry.getKey();
         }

         return dominantGenome;
    }

    public int getAverageAnimalsEnergy() {
        int sum = 0;
        for (Animal animal : this.animals)
            sum += animal.getEnergy();

        if (this.animals.size() == 0)
            return 0;

        return sum / this.animals.size();
    }

    public int getAverageAnimalsLifetime() {
        int sum = 0;
        for (Animal deadAnimal : this.deadAnimals)
            sum += deadAnimal.getAge();

        if (this.deadAnimals.size() == 0)
            return 0;

        return sum / this.deadAnimals.size();
    }

    public double getAverageChildrenNumber() {
        int sum = 0;
        for (Animal animal : this.animals)
            sum += animal.getNumberOfChildren();

        if (this.animals.size() == 0)
            return 0;

        return (double) sum / (double) this.animals.size();
    }

    public List<Animal> getDominantAnimals() {
        List <Animal> dominantAnimals = new ArrayList<>();
        Genome dominantGenome = this.getDominantGenome();

        for(Animal animal : animals) {
            if (animal.getGenome().equals(dominantGenome))
                dominantAnimals.add(animal);
        }
        return dominantAnimals;
    }

    private void checkGrassConsumption(Set <Vector2d> positions) {
        for (Vector2d position : positions) {
            Grass grass = null;

            for (Object element : this.elementsMap.get(position)) {
                if (element instanceof Grass) {
                    grass = (Grass) element;
                    break;
                }
            }

            if (grass != null) {
                Animal eater = null;

                // finding the highest energy level
                for (Object element : this.elementsMap.get(position)) {
                    if (element instanceof Animal &&
                            (eater == null || ((Animal) element).getEnergy() > eater.getEnergy()))
                        eater = (Animal) element;
                }

                if (eater != null) {
                    // finding all eaters (animals with the same energy)
                    List<Animal> eaters = new ArrayList<>();
                    int eatersEnergy = eater.getEnergy();

                    for (Object element : this.elementsMap.get(position)) {
                        if (element instanceof Animal && ((Animal) element).getEnergy() == eatersEnergy)
                            eaters.add((Animal) element);
                    }

                    for (Animal animal : eaters)
                        animal.consumeGrass(this.plantEnergy/eaters.size());

                    this.grasses.remove(grass);
                    this.elementsMap.get(position).remove(grass);
                }
            }
        }
    }

    private void checkReproduction(Set <Vector2d> positions) {
        for (Vector2d position : positions) {
            if (this.elementsMap.get(position).size() >=2) {  // there are at least 2 animals to reproduce
                Animal firstAnimal = null;
                Animal secondAnimal = null;

                for (Object element : this.elementsMap.get(position)) {  // finding a pair with the highest level of energy
                    Animal currAnimal = (Animal) element;
                    if (firstAnimal == null || currAnimal.getEnergy() > firstAnimal.getEnergy()) {
                        secondAnimal = firstAnimal;
                        firstAnimal = currAnimal;
                    }
                    else if (secondAnimal == null || currAnimal.getEnergy() > secondAnimal.getEnergy())
                        secondAnimal = currAnimal;
                }

                if (firstAnimal != null && secondAnimal != null &&
                        firstAnimal.hasMinimumReproductionEnergy() && secondAnimal.hasMinimumReproductionEnergy()) {
                    Animal babyAnimal = firstAnimal.reproduce(
                            secondAnimal, this.worldBuilder.getBabyPosition(firstAnimal.getPosition()));

                    this.addAnimal(babyAnimal);
                }
            }
        }
    }

    private void getRidOfDeadAnimals() {
        List<Animal> animalsToRemove = new ArrayList<>();
        for (Animal animal : this.animals) {
            if (animal.isDead()) {
                animalsToRemove.add(animal);
            }
        }

        for (Animal animal : animalsToRemove) {
            this.removeAnimal(animal);
        }
    }

    private String draw(){
        MapVisualizerX mapVisualizerX = new MapVisualizerX(this);

        Vector2d leftDown = new Vector2d(0,0);
        Vector2d rightUp = new Vector2d(this.width-1, this.height-1);

        return mapVisualizerX.draw(leftDown, rightUp);
    }
}
