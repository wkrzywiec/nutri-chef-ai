import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ExternalLink } from "lucide-react"

function RecipeCard({ recipe }) {
  return (
    <Card className="overflow-hidden flex flex-col">
      {recipe.imageUrl && (
        <div className="h-40 w-full overflow-hidden">
          <img
            src={recipe.imageUrl}
            alt={recipe.name}
            className="h-full w-full object-cover"
          />
        </div>
      )}
      <CardHeader className="pb-1 pt-3">
        <CardTitle className="text-sm font-semibold leading-snug">
          {recipe.name || "Unknown recipe"}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 pb-3 flex-1">
        {recipe.description && (
          <p className="text-xs text-muted-foreground line-clamp-3">
            {recipe.description}
          </p>
        )}
        {recipe.sourceUrl && (
          <a
            href={recipe.sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
          >
            <ExternalLink className="h-3 w-3" />
            {recipe.source || "Source"}
          </a>
        )}
      </CardContent>
    </Card>
  )
}

export default function RecipeGrid() {
  const recipes = props.recipes || []

  return (
    <div className="grid grid-cols-3 gap-4 w-full">
      {recipes.map((recipe, i) => (
        <RecipeCard key={i} recipe={recipe} />
      ))}
    </div>
  )
}
